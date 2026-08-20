package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.domain.ComplianceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Thay cho task "process_account_in_conductor" (đẩy data vào core banking +
 * chấm compliance). Ở prototype: giả lập kết quả bằng rule theo số cuối SĐT
 * (đoán trước được, dễ demo), hoặc random nếu cấu hình strategy=RANDOM.
 * Có thể ép kết quả qua forceResult để test nhanh cả 3 nhánh SUCCESS/NEED_REVIEW/FAILED.
 */
@Service
@RequiredArgsConstructor
public class ComplianceMockService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingProperties properties;

    public ComplianceStatus decide(String phone, String forceResult) {
        if (forceResult != null && !forceResult.isBlank()) {
            return ComplianceStatus.valueOf(forceResult.trim().toUpperCase());
        }

        if ("RANDOM".equalsIgnoreCase(properties.getComplianceMock().getStrategy())) {
            int roll = RANDOM.nextInt(100);
            if (roll < 80) return ComplianceStatus.SUCCESS;
            if (roll < 95) return ComplianceStatus.NEED_REVIEW;
            return ComplianceStatus.FAILED;
        }

        // RULE_BASED: số cuối SĐT 8/9 -> NEED_REVIEW, 0 -> FAILED, còn lại SUCCESS.
        char last = phone.charAt(phone.length() - 1);
        return switch (last) {
            case '8', '9' -> ComplianceStatus.NEED_REVIEW;
            case '0' -> ComplianceStatus.FAILED;
            default -> ComplianceStatus.SUCCESS;
        };
    }

    public String failureReasonFor(ComplianceStatus status) {
        return status == ComplianceStatus.FAILED
                ? "Compliance từ chối mở tài khoản (mock rule)"
                : null;
    }
}
