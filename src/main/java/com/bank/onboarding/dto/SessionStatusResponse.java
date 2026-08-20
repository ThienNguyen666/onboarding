package com.bank.onboarding.dto;

public record SessionStatusResponse(
        String sessionId,
        String phase,
        String status,
        String customerType,
        boolean dropoff,
        int ocrRetryCount,
        int livenessRetryCount,
        int nfcRetryCount,
        boolean otpVerified,
        String ebankUserId,
        String accountNumber,
        String linkId,
        String complianceStatus,
        String terminationReason
) {}
