package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.domain.ComplianceStatus;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.util.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceMockService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingProperties properties;

    public ComplianceStatus decide(String phone, String forceResult) {
        if (forceResult != null && !forceResult.isBlank()) {
            return forceResult(forceResult);
        }
        ComplianceStatus result = "RANDOM".equalsIgnoreCase(properties.complianceMock().strategy())
                ? decideRandom()
                : decideRuleBased(phone);
        log.debug("Compliance decision phone={} strategy={} result={}",
                Masking.phone(phone), properties.complianceMock().strategy(), result);
        return result;
    }

    // FIX: trước đây ComplianceStatus.valueOf ném IllegalArgumentException thô -> rơi vào
    // handler generic -> trả 500. Nay validate rõ ràng -> 400 BAD_REQUEST.
    private ComplianceStatus forceResult(String forceResult) {
        try {
            ComplianceStatus forced = ComplianceStatus.valueOf(forceResult.trim().toUpperCase());
            log.info("Compliance result forced to {} (QA/demo override)", forced);
            return forced;
        } catch (IllegalArgumentException e) {
            throw OnboardingException.badRequest(
                    "forceComplianceResult không hợp lệ: '" + forceResult
                            + "' (chỉ chấp nhận SUCCESS/NEED_REVIEW/FAILED)");
        }
    }

    private ComplianceStatus decideRandom() {
        int roll = RANDOM.nextInt(100);
        if (roll < 80) return ComplianceStatus.SUCCESS;
        if (roll < 95) return ComplianceStatus.NEED_REVIEW;
        return ComplianceStatus.FAILED;
    }

    private ComplianceStatus decideRuleBased(String phone) {
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