package com.bank.onboarding.conductor;

import com.bank.onboarding.domain.ComplianceStatus;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.entity.AuditLogEntry;
import com.bank.onboarding.repository.AuditLogRepository;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.bank.onboarding.service.*;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingConductorWorkers {

      private final CustomerDirectoryService customerDirectoryService;
      private final MockEkycService mockEkycService;
      private final OtpService otpService;
      private final ComplianceMockService complianceMockService;
      private final NotificationMockService notificationMockService;
      private final OnboardingSessionRepository sessionRepository;
      private final AuditLogRepository auditLogRepository;

      private Map<String, Object> notifyVendor(String vendorId, String refId, String status, String reason) {
            notificationMockService.notifyVendor(vendorId, refId, status, reason);
            return Map.of("notified", true);
      }

      private Map<String, Object> sendOtt(String customerId, String templateId) {
            notificationMockService.sendOtt(customerId, customerId, templateId);
            return Map.of("sent", true);
      }
      // ---------------- Phase 0 ----------------
      @WorkerTask("get_vendor_access_token")
      public Map<String, Object> getVendorAccessToken() {
            return Map.of("accessToken", UUID.randomUUID().toString());
      }

      @WorkerTask("show_cvp_and_confirm_consent")
      public Map<String, Object> showCvpAndConfirmConsent() {
            return Map.of("awaitingCustomerAction", true);
      }

      // ---------------- Phase 1 ----------------
      @WorkerTask("check_device_and_nfc")
      public Map<String, Object> checkDeviceAndNfc(@InputParam("deviceInfo") Map<String, Object> deviceInfo) {
            boolean nfcSupported = deviceInfo != null && Boolean.TRUE.equals(deviceInfo.get("nfcSupported"));
            return Map.of("device_eligible", nfcSupported);
      }

      // ---------------- Phase 2 ----------------
      @WorkerTask("collect_phone_number")
      public Map<String, Object> collectPhoneNumber() {
            return Map.of("awaitingCustomerAction", true);
      }
      
      @WorkerTask("check_customer_by_phone")
      public Map<String, Object> checkCustomerByPhone(@InputParam("phone") String phone) {
            CustomerType type = customerDirectoryService.lookupType(phone);
            String customerId = type == CustomerType.ETB
                  ? customerDirectoryService.findEtb(phone).orElseThrow().getCustomerId()
                  : customerDirectoryService.newNtbCustomerId();
            return Map.of("customerType", type.name(), "customerId", customerId);
      }

      @WorkerTask("handle_etb_customer")
      public Map<String, Object> handleEtbCustomer() {
            return Map.of("handled", true);
      }

      @WorkerTask("check_dropoff")
      public Map<String, Object> checkDropoff(@InputParam("phone") String phone) {
            Optional<CustomerDirectoryService.DropoffInfo> dropoff = customerDirectoryService.findDropoff(phone);
            if (dropoff.isEmpty()) {
                  return Map.of("isDropoff", false);
            }
            customerDirectoryService.clearDropoff(phone);
            return Map.of("isDropoff", true, "resumeStep", dropoff.get().resumeStep(),
                  "dropoffData", Map.of("sdkSessionId", dropoff.get().sdkSessionId()));
      }

      @WorkerTask("show_dropoff_screen")
      public Map<String, Object> showDropoffScreen() {
            return Map.of("shown", true);
      }

      // ---------------- Phase 3/4/5: OCR / Liveness / NFC retry-loop ----------------
      @WorkerTask("show_ocr_guide")
      public Map<String, Object> showOcrGuide() { return Map.of("shown", true); }

      @WorkerTask("perform_ocr_cccd")
      public Map<String, Object> performOcrCccd() {
            return Map.of("awaitingCustomerAction", true);
      }

      @WorkerTask("validate_ocr")
      public Map<String, Object> validateOcr(@InputParam("ocrData") Map<String, Object> ocrData,
                                          @InputParam("forceFail") Boolean forceFail) {
            boolean passed = mockEkycService.decidePassed(Boolean.TRUE.equals(forceFail));
            Map<String, Object> cccdData = (passed && ocrData != null) ? ocrData : Map.of();
            return Map.of("passed", passed, "cccdData", cccdData);
      }

      @WorkerTask("show_liveness_guide")
      public Map<String, Object> showLivenessGuide() { return Map.of("shown", true); }

      @WorkerTask("perform_liveness")
      public Map<String, Object> performLiveness() {
            return Map.of("awaitingCustomerAction", true);
      }

      @WorkerTask("validate_liveness")
      public Map<String, Object> validateLiveness(@InputParam("livenessData") Map<String, Object> livenessData,
                                                @InputParam("forceFail") Boolean forceFail) {
            boolean passed = mockEkycService.decidePassed(Boolean.TRUE.equals(forceFail));
            Map<String, Object> data = (passed && livenessData != null) ? livenessData : Map.of();
            return Map.of("passed", passed, "livenessData", data);
      }

      @WorkerTask("show_nfc_guide")
      public Map<String, Object> showNfcGuide() { return Map.of("shown", true); }

      @WorkerTask("perform_nfc")
      public Map<String, Object> performNfc() {
            return Map.of("awaitingCustomerAction", true);
      }

      @WorkerTask("validate_nfc")
      public Map<String, Object> validateNfc(@InputParam("nfcData") Map<String, Object> nfcData,
                                          @InputParam("forceFail") Boolean forceFail) {
            boolean passed = mockEkycService.decidePassed(Boolean.TRUE.equals(forceFail));
            Map<String, Object> data = (passed && nfcData != null) ? nfcData : Map.of();
            return Map.of("passed", passed, "nfcData", data);
      }

      // ---------------- Phase 6 ----------------
      @WorkerTask("show_identity_confirmation")
      public Map<String, Object> showIdentityConfirmation() {
            return Map.of("awaitingCustomerAction", true);
      }

      @WorkerTask("check_customer_type_and_age")
      public Map<String, Object> checkCustomerTypeAndAge(@InputParam("cccdData") Map<String, Object> cccdData) {
            int age = mockEkycService.ageFromCccd(cccdData);
            return Map.of("customerType", age < 18 ? "UNDERAGE" : "NTB", "age", age);
      }

      @WorkerTask("show_tnc_screen")
      public Map<String, Object> showTncScreen() {
            return Map.of("awaitingCustomerAction", true);
      }
      @WorkerTask("send_otp")
      public Map<String, Object> sendOtp(@InputParam("phone") String phone) {
            String workflowSessionKey = OtpService.workflowSessionKey(phone);
            String otpToken = otpService.generateAndStore(workflowSessionKey);
            log.info("OTP sent for phone (masked in service layer)");
            return Map.of("otpToken", otpToken);
      }

      @WorkerTask("verify_otp")
      public Map<String, Object> verifyOtp() {
            return Map.of("awaitingCustomerAction", true);
      }

      // ---------------- Phase 7 ----------------
      @WorkerTask("create_ebank_user")
      public Map<String, Object> createEbankUser(@InputParam("customerId") String customerId) {
            String ebankUserId = "EB-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            String accountNumber = generateAccountNumber();
            log.info("Created ebank user for customerId={} -> {}", customerId, ebankUserId);
            return Map.of("ebankUserId", ebankUserId, "accountNumber", accountNumber);
      }

      @WorkerTask("show_result_to_customer")
      public Map<String, Object> showResultToCustomer() { return Map.of("shown", true); }

      @WorkerTask("process_account_in_conductor")
      public Map<String, Object> processAccountInConductor(@InputParam("phone") String phone,
                                                            @InputParam("forceComplianceResult") String forceComplianceResult) {
            ComplianceStatus status = complianceMockService.decide(phone, forceComplianceResult);
            String reason = complianceMockService.failureReasonFor(status);
            return reason == null ? Map.of("status", status.name()) : Map.of("status", status.name(), "failureReason", reason);
      }

      @WorkerTask("create_link_id")
      public Map<String, Object> createLinkId() {
            return Map.of("linkId", "LINK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
      }

      @WorkerTask("notify_vendor_success")
      public Map<String, Object> notifyVendorSuccess(@InputParam("vendorId") String vendorId,
                                                      @InputParam("ebankUserId") String ebankUserId) {
            return notifyVendor(vendorId, ebankUserId, "SUCCESS", null);
      }

      @WorkerTask("notify_vendor_need_review")
      public Map<String, Object> notifyVendorNeedReview(@InputParam("vendorId") String vendorId,
                                                            @InputParam("customerId") String customerId) {
            return notifyVendor(vendorId, customerId, "NEED_REVIEW", null);
      }

      @WorkerTask("notify_vendor_failed")
      public Map<String, Object> notifyVendorFailed(@InputParam("vendorId") String vendorId,
                                                      @InputParam("customerId") String customerId,
                                                      @InputParam("reason") String reason) {
            return notifyVendor(vendorId, customerId, "FAILED", reason);
      }

      @WorkerTask("notify_vendor_unknown_error")
      public Map<String, Object> notifyVendorUnknownError(@InputParam("vendorId") String vendorId,
                                                            @InputParam("customerId") String customerId) {
            return notifyVendor(vendorId, customerId, "FAILED", "Unknown conductor processing result");
      }

      @WorkerTask("send_ott_success")
      public Map<String, Object> sendOttSuccess(@InputParam("customerId") String customerId) {
            return sendOtt(customerId, "ACCOUNT_OPEN_SUCCESS");
      }

      @WorkerTask("send_ott_need_review")
      public Map<String, Object> sendOttNeedReview(@InputParam("customerId") String customerId) {
            return sendOtt(customerId, "ACCOUNT_PENDING_REVIEW");
      }

      @WorkerTask("send_ott_failed")
      public Map<String, Object> sendOttFailed(@InputParam("customerId") String customerId) {
            return sendOtt(customerId, "ACCOUNT_OPEN_FAILED");
      }
      
      @WorkerTask("cleanup_vendor_sdk_session")
      public Map<String, Object> cleanupVendorSdkSession() {
            return Map.of("cleaned", true);
      }

      @WorkerTask("audit_log_final_result")
      public Map<String, Object> auditLogFinalResult(
            @InputParam("workflowId") String workflowId, @InputParam("finalStatus") String finalStatus,
            @InputParam("ebankUserId") String ebankUserId, @InputParam("accountNumber") String accountNumber,
            @InputParam("failureReason") String failureReason,
            @InputParam("customerId") String customerId, @InputParam("phone") String phone,
            @InputParam("cccdData") Map<String, Object> cccdData)
      {
            sessionRepository.findByWorkflowId(workflowId).ifPresent(session -> {
                  session.setLastKnownStatus(finalStatus);
                  sessionRepository.save(session);
            });
            auditLogRepository.save(new AuditLogEntry(workflowId, "FINAL_RESULT", Map.of(
                  "finalStatus", String.valueOf(finalStatus),
                  "ebankUserId", String.valueOf(ebankUserId),
                  "accountNumber", String.valueOf(accountNumber),
                  "failureReason", failureReason == null ? "" : failureReason)));

            // FIX: KH mở TK SUCCESS xong vẫn còn NTB trong customer_record -> test lại cùng SĐT
            // lần sau bị coi là NTB mới toanh (mất nhánh ETB). Convert ngay khi SUCCESS.
            if ("SUCCESS".equals(finalStatus) && phone != null && customerId != null) {
                  String fullName = cccdData != null ? String.valueOf(cccdData.getOrDefault("fullName", "")) : "";
                  customerDirectoryService.registerAsEtbIfAbsent(customerId, phone, fullName);
            }
            log.info("[AUDIT] workflowId={} finalStatus={}", workflowId, finalStatus);
            return Map.of("logged", true);
      }
      private String generateAccountNumber() {
            var random = new java.security.SecureRandom();
            StringBuilder sb = new StringBuilder("9");
            for (int i = 0; i < 9; i++) sb.append(random.nextInt(10));
            return sb.toString();
      }
}