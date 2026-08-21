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
import com.bank.onboarding.util.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator thay thế cho engine Orkes Conductor: mỗi method public tương
 * ứng với một "phase" trong workflow gốc (vendor_sdk_ekyc_account_opening_2.json).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingOrchestrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingSessionRepository sessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final OnboardingProperties properties;
    private final CustomerDirectoryService customerDirectoryService;
    private final MockEkycService mockEkycService;
    private final OtpService otpService;
    private final ComplianceMockService complianceMockService;
    private final NotificationMockService notificationMockService;

    // ------------------------------------------------------------------
    // Phase 0 — init SDK.
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
        log.info("Session init id={} vendorId={} productType={}", session.getId(), req.vendorId(), req.productType());
        audit(session.getId(), "SESSION_INIT", Map.of("vendorId", req.vendorId(), "productType", req.productType()));
        return new InitSessionResponse(session.getId(), session.getAccessToken(), session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 1 — device + NFC check
    // ------------------------------------------------------------------
    @Transactional
    public DeviceCheckResponse checkDevice(String sessionId, DeviceCheckRequest req) {
        OnboardingSession session = require(sessionId, SessionPhase.DEVICE_CHECK);

        session.setDeviceModel(req.model());
        session.setDeviceOsVersion(req.osVersion());
        session.setDeviceNfcSupported(req.nfcSupported());
        boolean eligible = req.nfcSupported(); // rule tối giản cho prototype
        session.setDeviceEligible(eligible);

        if (!eligible) {
            session.terminate(SessionStatus.FAILED, "Thiết bị không thỏa điều kiện hoặc không hỗ trợ NFC");
            sessionRepository.save(session);
            log.info("Session {} terminated: device not eligible (model={})", sessionId, req.model());
            return new DeviceCheckResponse(false, session.getTerminationReason(), session.getPhase().name());
        }

        session.setPhase(SessionPhase.CUSTOMER_LOOKUP);
        sessionRepository.save(session);
        return new DeviceCheckResponse(true, null, session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 2 — ETB/NTB + dropoff.
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
            log.info("Session {} -> ETB redirect, customerId={}", sessionId, etb.getCustomerId());
            return new CustomerLookupResponse("ETB", false, null, session.getPhase().name());
        }

        // NTB
        Optional<CustomerDirectoryService.DropoffInfo> dropoff = customerDirectoryService.findDropoff(req.phone());
        if (dropoff.isPresent()) {
            return resumeFromDropoff(session, req.phone(), dropoff.get());
        }

        session.setCustomerId(customerDirectoryService.newNtbCustomerId());
        session.setPhase(SessionPhase.OCR);
        sessionRepository.save(session);
        return new CustomerLookupResponse("NTB", false, null, session.getPhase().name());
    }

    /**
     * FIX: mỗi lần mở SDK, init() tạo 1 session row hoàn toàn mới -> nếu chỉ set
     * phase=resumeStep mà không copy lại cccdData/livenessData/nfcData/retryCount
     * từ session cũ, các bước sau (VD confirmIdentity cần cccdData) sẽ thiếu dữ liệu
     * và lỗi. Dùng findTopByPhoneOrderByCreatedAtDesc (vốn có sẵn nhưng chưa từng
     * được gọi) để lấy lại tiến độ session trước đó.
     */
    private CustomerLookupResponse resumeFromDropoff(OnboardingSession session, String phone,
                                                       CustomerDirectoryService.DropoffInfo dropoff) {
        SessionPhase resumePhase;
        try {
            resumePhase = SessionPhase.valueOf(dropoff.resumeStep());
        } catch (IllegalArgumentException e) {
            log.warn("Dropoff resumeStep không hợp lệ '{}' cho phone={}, bỏ qua dropoff", dropoff.resumeStep(), Masking.phone(phone));
            resumePhase = SessionPhase.OCR;
        }

        Optional<OnboardingSession> previous = sessionRepository.findTopByPhoneOrderByCreatedAtDesc(phone);
        if (previous.isPresent()) {
            copyProgress(session, previous.get());
            log.info("Resuming dropoff: phone={} newSession={} fromSession={} resumePhase={}",
                    Masking.phone(phone), session.getId(), previous.get().getId(), resumePhase);
        } else {
            log.warn("Dropoff flag tồn tại cho phone={} nhưng không tìm thấy session cũ — bắt đầu lại từ OCR",
                    Masking.phone(phone));
            session.setCustomerId(customerDirectoryService.newNtbCustomerId());
            resumePhase = SessionPhase.OCR;
        }

        session.setDropoff(true);
        session.setPhase(resumePhase);
        customerDirectoryService.clearDropoff(phone);
        sessionRepository.save(session);
        return new CustomerLookupResponse("NTB", true, resumePhase.name(), session.getPhase().name());
    }

    private void copyProgress(OnboardingSession target, OnboardingSession source) {
        target.setCustomerId(source.getCustomerId());
        target.setOcrPassed(source.getOcrPassed());
        target.setCccdData(source.getCccdData());
        target.setOcrRetryCount(source.getOcrRetryCount());
        target.setLivenessPassed(source.getLivenessPassed());
        target.setLivenessData(source.getLivenessData());
        target.setLivenessRetryCount(source.getLivenessRetryCount());
        target.setNfcPassed(source.getNfcPassed());
        target.setNfcData(source.getNfcData());
        target.setNfcRetryCount(source.getNfcRetryCount());
        target.setIdentityConfirmed(source.isIdentityConfirmed());
        target.setTncAccepted(source.isTncAccepted());
    }

    // ------------------------------------------------------------------
    // Phase 3/4/5 — OCR / Liveness / NFC, cùng 1 khuôn retry-loop.
    // ------------------------------------------------------------------
    @Transactional
    public EkycStepResponse submitOcr(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.OCR, SessionPhase.LIVENESS,
                properties.retry().defaultMaxOcrRetries(), req,
                (session, passed) -> {
                    session.setOcrPassed(passed);
                    session.setCccdData(mockEkycService.mockCccdData(req.mockPayload()));
                });
    }

    @Transactional
    public EkycStepResponse submitLiveness(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.LIVENESS, SessionPhase.NFC,
                properties.retry().defaultMaxLivenessRetries(), req,
                (session, passed) -> {
                    session.setLivenessPassed(passed);
                    session.setLivenessData(mockEkycService.mockLivenessData(req.mockPayload()));
                });
    }

    @Transactional
    public EkycStepResponse submitNfc(String sessionId, EkycStepRequest req) {
        return runEkycStep(sessionId, SessionPhase.NFC, SessionPhase.IDENTITY_CONFIRM,
                properties.retry().defaultMaxNfcRetries(), req,
                (session, passed) -> {
                    session.setNfcPassed(passed);
                    session.setNfcData(mockEkycService.mockNfcData(req.mockPayload()));
                });
    }

    private interface StepApplier {
        void apply(OnboardingSession session, boolean passed);
    }

    // Gọi nội bộ từ 3 method public phía trên (đã @Transactional bao ngoài) —
    // không tự đánh @Transactional ở đây vì self-invocation bị Spring AOP bỏ qua.
    private EkycStepResponse runEkycStep(String sessionId, SessionPhase currentPhase, SessionPhase nextPhase,
                                          int maxRetries, EkycStepRequest req, StepApplier applier) {
        OnboardingSession session = require(sessionId, currentPhase);
        boolean passed = mockEkycService.decidePassed(req.forceFail());
        applier.apply(session, passed);

        if (passed) {
            session.setPhase(nextPhase);
            sessionRepository.save(session);
            log.info("Session {} phase {} PASSED -> {}", sessionId, currentPhase, nextPhase);
            return new EkycStepResponse(true, retryCountOf(session, currentPhase), maxRetries, false,
                    session.getPhase().name(), session.getStatus().name());
        }

        int attempt = incrementRetry(session, currentPhase);
        boolean retryAllowed = attempt < maxRetries;
        if (!retryAllowed) {
            session.terminate(SessionStatus.FAILED, currentPhase.name() + " thất bại sau tối đa số lần thử lại");
            log.warn("Session {} phase {} FAILED after {} attempts", sessionId, currentPhase, attempt);
        } else {
            log.info("Session {} phase {} failed, attempt {}/{}, retry allowed", sessionId, currentPhase, attempt, maxRetries);
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
    // ------------------------------------------------------------------
    @Transactional
    public IdentityConfirmResponse confirmIdentity(String sessionId) {
        OnboardingSession session = require(sessionId, SessionPhase.IDENTITY_CONFIRM);
        int age = mockEkycService.ageFromCccd(session.getCccdData());

        if (age < 18) {
            session.terminate(SessionStatus.FAILED, "KH chưa đủ 18 tuổi, không đủ điều kiện mở tài khoản");
            sessionRepository.save(session);
            log.info("Session {} terminated: UNDERAGE (age={})", sessionId, age);
            return new IdentityConfirmResponse("UNDERAGE", session.getCccdData(),
                    session.getNfcData(), false, session.getPhase().name());
        }

        session.setIdentityConfirmed(true);
        session.setPhase(SessionPhase.TNC);
        sessionRepository.save(session);
        return new IdentityConfirmResponse("NTB", session.getCccdData(),
                session.getNfcData(), true, session.getPhase().name());
    }

    // ------------------------------------------------------------------
    // Phase 6b — TnC
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
    // Phase 6c — OTP
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
        return new OtpSendResponse(session.getPhase().name(), properties.otp().ttlSeconds());
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
        log.info("Session {} FINAL RESULT compliance={} ebankUserId={} accountNumber={}",
                sessionId, compliance, session.getEbankUserId(), session.getAccountNumber());
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
    @Transactional(readOnly = true)
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

    private void audit(String sessionId, String event, Map<String, Object> detail) {
        auditLogRepository.save(new AuditLogEntry(sessionId, event, detail));
    }
}