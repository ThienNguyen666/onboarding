package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.domain.ComplianceStatus;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.domain.SessionPhase;
import com.bank.onboarding.domain.SessionStatus;
import com.bank.onboarding.dto.AccountCreationDtos.CreateAccountRequest;
import com.bank.onboarding.dto.AccountCreationDtos.CreateAccountResponse;
import com.bank.onboarding.dto.CustomerLookupDtos.CustomerLookupResponse;
import com.bank.onboarding.dto.CustomerLookupDtos.PhoneRequest;
import com.bank.onboarding.dto.EkycStepDtos.EkycStepRequest;
import com.bank.onboarding.dto.EkycStepDtos.EkycStepResponse;
import com.bank.onboarding.dto.IdentityAndOtpDtos.*;
import com.bank.onboarding.dto.InitDtos.*;
import com.bank.onboarding.dto.SessionStatusResponse;
import com.bank.onboarding.entity.AuditLogEntry;
import com.bank.onboarding.entity.OnboardingSession;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.repository.AuditLogRepository;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrator thay thế cho engine Orkes Conductor: mỗi method public tương
 * ứng với một "phase" trong workflow gốc (vendor_sdk_ekyc_account_opening_2.json),
 * đọc/ghi trực tiếp lên OnboardingSession thay vì DO_WHILE/SWITCH/TERMINATE task.
 *
 * Các rút gọn có chủ đích so với workflow gốc (đã thống nhất là chấp nhận được
 * cho bản prototype) được ghi chú ngay tại chỗ liên quan.
 */
@Service
@RequiredArgsConstructor
public class OnboardingOrchestrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingSessionRepository sessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final OnboardingProperties properties;
    private final CustomerDirectoryService customerDirectoryService;
    private final MockEkycService mockEkycService;
    private final OtpService otpService;
    private final ComplianceMockService complianceMockService;
    private final NotificationMockService notificationMockService;

    // ------------------------------------------------------------------
    // Phase 0 — init SDK. Bỏ OAuth client-credential thật vì SDK là do
    // chính bank phát hành (không phải vendor thứ 3) -> sinh accessToken
    // nội bộ đủ dùng cho việc trace phiên, không cần xác thực vendor.
    // ------------------------------------------------------------------
    @Transactional
    public InitSessionResponse init(InitSessionRequest req) {
        OnboardingSession session = new OnboardingSession();
        session.setVendorId(req.vendorId());
        session.setSdkSessionId(req.sdkSessionId());
        session.setProductType(req.productType());
        session.setAccessToken(UUID.randomUUID().toString());
        session.setPhase(SessionPhase.DEVICE_CHECK);
        sessionRepository.save(session);
        audit(session.getId(), "SESSION_INIT", Map.of("vendorId", req.vendorId(), "productType", req.productType()));
        return new InitSessionResponse(session.getId(), session.getAccessToken(), session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 1 — device + NFC check
    // ------------------------------------------------------------------
    @SneakyThrows
    @Transactional
    public DeviceCheckResponse checkDevice(String sessionId, DeviceCheckRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.DEVICE_CHECK);

        session.setDeviceInfoJson(objectMapper.writeValueAsString(req));
        boolean eligible = req.nfcSupported(); // rule tối giản cho prototype: chỉ cần máy hỗ trợ NFC
        session.setDeviceEligible(eligible);

        if (!eligible) {
            session.terminate(SessionStatus.FAILED, "Thiết bị không thỏa điều kiện hoặc không hỗ trợ NFC");
            sessionRepository.save(session);
            return new DeviceCheckResponse(false, session.getTerminationReason(), session.getPhase().name());
        }

        session.setPhase(SessionPhase.CUSTOMER_LOOKUP);
        sessionRepository.save(session);
        return new DeviceCheckResponse(true, null, session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 2 — ETB/NTB + dropoff.
    // Rút gọn: bỏ luồng "handle_etb_customer" chi tiết (điều hướng qua app
    // ETB riêng) — prototype chỉ cần dừng lại và trả customerType=ETB để FE
    // tự điều hướng, không mô phỏng app ETB.
    // ------------------------------------------------------------------
    @Transactional
    public CustomerLookupResponse lookupCustomer(String sessionId, PhoneRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.CUSTOMER_LOOKUP);
        session.setPhone(req.phone());

        CustomerType type = customerDirectoryService.lookupType(req.phone());
        session.setCustomerType(type);

        if (type == CustomerType.ETB) {
            var etb = customerDirectoryService.findEtb(req.phone()).orElseThrow();
            session.setCustomerId(etb.getCustomerId());
            session.terminate(SessionStatus.SUCCESS,
                    "ETB_REDIRECT: KH đã có tài khoản, điều hướng sang luồng ETB (không tính vào tỷ lệ mở TK NTB)");
            sessionRepository.save(session);
            return new CustomerLookupResponse("ETB", false, null, session.getPhase().name());
        }

        // NTB
        var dropoff = customerDirectoryService.findDropoff(req.phone());
        if (dropoff.isPresent()) {
            String resumeStep = dropoff.get()[1];
            session.setCustomerId(customerDirectoryService.newNtbCustomerId());
            session.setDropoff(true);
            session.setPhase(SessionPhase.valueOf(resumeStep));
            sessionRepository.save(session);
            return new CustomerLookupResponse("NTB", true, resumeStep, session.getPhase().name());
        }

        session.setCustomerId(customerDirectoryService.newNtbCustomerId());
        session.setPhase(SessionPhase.OCR);
        sessionRepository.save(session);
        return new CustomerLookupResponse("NTB", false, null, session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 3/4/5 — OCR / Liveness / NFC, cùng 1 khuôn retry-loop.
    // Rút gọn: thay DO_WHILE + SET_VARIABLE (đặc thù engine Orkes) bằng
    // counter field trực tiếp trên OnboardingSession — kết quả tương đương,
    // không cần workflow.variables.
    // ------------------------------------------------------------------
    @Transactional
    public EkycStepResponse submitOcr(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.OCR, SessionPhase.LIVENESS,
                properties.getRetry().getDefaultMaxOcrRetries(), req,
                (session, passed) -> {
                    session.setOcrPassed(passed);
                    session.setCccdDataJson(mockEkycService.mockCccdData(req.mockPayload()));
                });
    }

    @Transactional
    public EkycStepResponse submitLiveness(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.LIVENESS, SessionPhase.NFC,
                properties.getRetry().getDefaultMaxLivenessRetries(), req,
                (session, passed) -> {
                    session.setLivenessPassed(passed);
                    session.setLivenessDataJson(mockEkycService.mockLivenessData(req.mockPayload()));
                });
    }

    @Transactional
    public EkycStepResponse submitNfc(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.NFC, SessionPhase.IDENTITY_CONFIRM,
                properties.getRetry().getDefaultMaxNfcRetries(), req,
                (session, passed) -> {
                    session.setNfcPassed(passed);
                    session.setNfcDataJson(mockEkycService.mockNfcData(req.mockPayload()));
                });
    }

    private interface StepApplier {
        void apply(OnboardingSession session, boolean passed);
    }

    // Gọi nội bộ từ 3 method public phía trên (đã có @Transactional bao ngoài) —
    // không tự đánh @Transactional ở đây vì self-invocation sẽ bị Spring AOP bỏ qua.
    private EkycStepResponse runEkycStep(String sessionId, SessionPhase currentPhase, SessionPhase nextPhase,
                                          int maxRetries, EkycStepRequest req, StepApplier applier) {
        OnboardingSession session = require(sessionId, currentPhase);
        boolean passed = mockEkycService.decidePassed(req.forceFail());
        applier.apply(session, passed);

        if (passed) {
            session.setPhase(nextPhase);
            sessionRepository.save(session);
            return new EkycStepResponse(true, retryCountOf(session, currentPhase), maxRetries, false,
                    session.getPhase().name(), session.getStatus().name());
        }

        int attempt = incrementRetry(session, currentPhase);
        boolean retryAllowed = attempt < maxRetries;
        if (!retryAllowed) {
            session.terminate(SessionStatus.FAILED, currentPhase.name() + " thất bại sau tối đa số lần thử lại");
        }
        sessionRepository.save(session);
        return new EkycStepResponse(false, attempt, maxRetries, retryAllowed,
                session.getPhase().name(), session.getStatus().name());
    }

    private int incrementRetry(OnboardingSession session, SessionPhase phase) {
        int count = retryCountOf(session, phase) + 1;
        switch (phase) {
            case OCR -> session.setOcrRetryCount(count);
            case LIVENESS -> session.setLivenessRetryCount(count);
            case NFC -> session.setNfcRetryCount(count);
            default -> throw new IllegalStateException("Not a retry phase: " + phase);
        }
        return count;
    }

    private int retryCountOf(OnboardingSession session, SessionPhase phase) {
        return switch (phase) {
            case OCR -> session.getOcrRetryCount();
            case LIVENESS -> session.getLivenessRetryCount();
            case NFC -> session.getNfcRetryCount();
            default -> 0;
        };
    }

    // ------------------------------------------------------------------
    // Phase 6a — xác nhận định danh + kiểm tra tuổi >= 18.
    // Rút gọn: bỏ việc re-derive ETB từ GTTT (đã chốt ETB/NTB ở phase 2);
    // ở đây chỉ còn check tuổi vì đó là phần logic nghiệp vụ thực, phần
    // ETB-detect-lại cần dữ liệu core banking thật mới mô phỏng có ý nghĩa.
    // ------------------------------------------------------------------
    @Transactional
    public IdentityConfirmResponse confirmIdentity(String sessionId) {
        OnboardingSession session = require(sessionId, SessionPhase.IDENTITY_CONFIRM);
        int age = mockEkycService.ageFromCccd(session.getCccdDataJson());

        if (age < 18) {
            session.terminate(SessionStatus.FAILED, "KH chưa đủ 18 tuổi, không đủ điều kiện mở tài khoản");
            sessionRepository.save(session);
            return new IdentityConfirmResponse("UNDERAGE", readJson(session.getCccdDataJson()),
                    readJson(session.getNfcDataJson()), false, session.getPhase().name());
        }

        session.setIdentityConfirmed(true);
        session.setPhase(SessionPhase.TNC);
        sessionRepository.save(session);
        return new IdentityConfirmResponse("NTB", readJson(session.getCccdDataJson()),
                readJson(session.getNfcDataJson()), true, session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 6b — TnC (nội dung TnC theo productType do FE tự render tuỳ
    // TKTT hay TKTT+Debit, backend chỉ ghi nhận đồng ý).
    // ------------------------------------------------------------------
    @Transactional
    public void acceptTnc(String sessionId, TncAcceptRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.TNC);
        if (!session.isIdentityConfirmed()) {
            throw OnboardingException.badState("Chưa xác nhận định danh");
        }
        session.setTncAccepted(true);
        session.setPhase(SessionPhase.OTP);
        sessionRepository.save(session);
        audit(sessionId, "TNC_ACCEPTED", Map.of("tncVersion", req.tncVersion()));
    }

    // ------------------------------------------------------------------
    // Phase 6c — OTP. Rút gọn: gen random 6 số lưu Redis TTL thay vì gọi
    // gateway SMS thật; có endpoint debug riêng để xem OTP khi test (xem
    // DebugController) thay vì phải đọc SMS.
    // ------------------------------------------------------------------
    @Transactional
    public OtpSendResponse sendOtp(String sessionId) {
        OnboardingSession session = require(sessionId, SessionPhase.OTP);
        if (!session.isTncAccepted()) {
            throw OnboardingException.badState("Chưa xác nhận TnC");
        }
        otpService.generateAndStore(sessionId);
        session.setOtpTokenRef(sessionId);
        sessionRepository.save(session);
        return new OtpSendResponse(session.getPhase().name(), properties.getOtp().getTtlSeconds());
    }

    @Transactional
    public OtpVerifyResponse verifyOtp(String sessionId, OtpVerifyRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.OTP);
        boolean verified = otpService.verify(sessionId, req.otp());

        if (!verified) {
            sessionRepository.save(session);
            return new OtpVerifyResponse(false, otpService.attemptsLeft(sessionId), session.getPhase().name());
        }

        session.setOtpVerified(true);
        session.setPhase(SessionPhase.ACCOUNT_CREATION);
        sessionRepository.save(session);
        customerDirectoryService.clearDropoff(session.getPhone());
        return new OtpVerifyResponse(true, otpService.attemptsLeft(sessionId), session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 7 — tạo tài khoản + "xử lý tiếp theo trong Conductor".
    // Rút gọn lớn nhất của prototype: FORK_JOIN (show_result_to_customer //
    // process_account_in_conductor) gộp lại tuần tự trong 1 transaction vì
    // không có core banking thật để gọi song song; kết quả compliance được
    // mock bằng rule theo SĐT (xem ComplianceMockService). NEED_REVIEW vẫn
    // giữ đúng nguyên tắc "không polling trực tiếp lên workflow" — chỉ trả
    // trạng thái, cập nhật sau qua kênh khác nếu cần.
    // ------------------------------------------------------------------
    @Transactional
    public CreateAccountResponse createAccount(String sessionId, CreateAccountRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.ACCOUNT_CREATION);
        if (!session.isOtpVerified()) {
            throw OnboardingException.badState("Chưa xác thực OTP");
        }

        session.setEbankUserId("EB-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        session.setAccountNumber(generateAccountNumber());

        ComplianceStatus compliance = complianceMockService.decide(session.getPhone(), req.forceComplianceResult());
        session.setComplianceStatus(compliance);
        session.setFailureReason(complianceMockService.failureReasonFor(compliance));

        switch (compliance) {
            case SUCCESS -> {
                session.setLinkId("LINK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                session.terminate(SessionStatus.SUCCESS, null);
                notificationMockService.notifyVendor(session.getVendorId(), sessionId, "SUCCESS", null);
                notificationMockService.sendOtt(sessionId, session.getCustomerId(), "ACCOUNT_OPEN_SUCCESS");
            }
            case NEED_REVIEW -> {
                session.terminate(SessionStatus.NEED_REVIEW, "Hồ sơ cần review thủ công (mock)");
                notificationMockService.notifyVendor(session.getVendorId(), sessionId, "NEED_REVIEW", null);
                notificationMockService.sendOtt(sessionId, session.getCustomerId(), "ACCOUNT_PENDING_REVIEW");
            }
            case FAILED -> {
                session.terminate(SessionStatus.FAILED, session.getFailureReason());
                notificationMockService.notifyVendor(session.getVendorId(), sessionId, "FAILED", session.getFailureReason());
                notificationMockService.sendOtt(sessionId, session.getCustomerId(), "ACCOUNT_OPEN_FAILED");
            }
        }

        sessionRepository.save(session);
        audit(sessionId, "FINAL_RESULT", Map.of(
                "complianceStatus", compliance.name(),
                "ebankUserId", session.getEbankUserId(),
                "accountNumber", session.getAccountNumber()));

        return new CreateAccountResponse(
                session.getEbankUserId(), session.getAccountNumber(), compliance.name(),
                session.getLinkId(), session.getFailureReason(),
                session.getPhase().name(), session.getStatus().name());
    }

    // ------------------------------------------------------------------
    // Query / debug
    // ------------------------------------------------------------------
    public SessionStatusResponse status(String sessionId) {
        OnboardingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> OnboardingException.notFound("Không tìm thấy phiên: " + sessionId));
        return new SessionStatusResponse(
                session.getId(), session.getPhase().name(), session.getStatus().name(),
                session.getCustomerType() == null ? null : session.getCustomerType().name(),
                session.isDropoff(), session.getOcrRetryCount(), session.getLivenessRetryCount(),
                session.getNfcRetryCount(), session.isOtpVerified(), session.getEbankUserId(),
                session.getAccountNumber(), session.getLinkId(),
                session.getComplianceStatus() == null ? null : session.getComplianceStatus().name(),
                session.getTerminationReason());
    }

    /** Đánh dấu dropoff thủ công (FE gọi khi user thoát app giữa chừng ở 1 phase còn dở). */
    @Transactional
    public void markDropoff(String sessionId) {
        OnboardingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> OnboardingException.notFound("Không tìm thấy phiên: " + sessionId));
        if (session.getPhone() != null && session.getStatus() == SessionStatus.IN_PROGRESS) {
            customerDirectoryService.markDropoff(session.getPhone(), sessionId, session.getPhase().name());
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private OnboardingSession require(String sessionId, SessionPhase expectedPhase) {
        OnboardingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> OnboardingException.notFound("Không tìm thấy phiên: " + sessionId));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw OnboardingException.badState("Phiên đã kết thúc với trạng thái " + session.getStatus());
        }
        if (session.getPhase() != expectedPhase) {
            throw OnboardingException.badState("Phiên đang ở phase " + session.getPhase() + ", không phải " + expectedPhase);
        }
        return session;
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder("9");
        for (int i = 0; i < 9; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @SneakyThrows
    private Object readJson(String json) {
        return json == null ? null : objectMapper.readValue(json, Map.class);
    }

    @SneakyThrows
    private void audit(String sessionId, String event, Map<String, Object> detail) {
        auditLogRepository.save(new AuditLogEntry(sessionId, event, objectMapper.writeValueAsString(detail)));
    }
}
