package com.bank.onboarding.dto;

public class IdentityAndOtpDtos {

        public record OtpDebugResponse(
                String key,
                String otp,
                long ttlSecondsRemaining
        ) {}

        private IdentityAndOtpDtos() {}
}