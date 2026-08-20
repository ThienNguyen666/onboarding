package com.bank.onboarding.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thay cho các task "notify_vendor_*" và "send_ott_*": prototype chỉ log ra,
 * không tích hợp SMS/email/webhook gateway thật. Giữ interface rõ ràng để
 * sau này cắm implementation thật vào không phải sửa OnboardingOrchestrationService.
 */
@Service
@Slf4j
public class NotificationMockService {

    public void notifyVendor(String vendorId, String sessionId, String status, String reason) {
        log.info("[MOCK-WEBHOOK] -> vendor={} session={} status={} reason={}", vendorId, sessionId, status, reason);
    }

    public void sendOtt(String sessionId, String customerId, String templateId) {
        log.info("[MOCK-OTT] session={} customerId={} template={}", sessionId, customerId, templateId);
    }
}
