package com.bank.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class IdentityAndOtpDtos {

    public record IdentityConfirmResponse(
            String customerType,   // NTB (hợp lệ) | ETB | UNDERAGE
            Object cccdData,
            Object nfcData,
            boolean eligible,
            String phase
    ) {}

    public record TncAcceptRequest(
            @NotBlank String tncVersion
    ) {}

    public record OtpSendResponse(
            String phase,
            int expiresInSeconds
    ) {}

    public record OtpVerifyRequest(
            @NotBlank @Pattern(regexp = "\\d{4,8}") String otp
    ) {}

    public record OtpVerifyResponse(
            boolean verified,
            int attemptsLeft,
            String phase
    ) {}

    /** Chỉ bật khi app.onboarding.otp.debug-endpoint-enabled=true (dev/local). */
    public record OtpDebugResponse(
            String sessionId,
            String otp,
            long ttlSecondsRemaining
    ) {}

    private IdentityAndOtpDtos() {}
}
