package com.bank.onboarding.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình nghiệp vụ cho luồng eKYC onboarding (prototype).
 * Dùng record + constructor binding thay vì getter/setter thủ công:
 * immutable, ít boilerplate, tự validate qua JSR-303 (không cần thêm @Validated).
 */
@ConfigurationProperties(prefix = "app.onboarding")
public record OnboardingProperties(
        Retry retry,
        Otp otp,
        Dropoff dropoff,
        EkycMock ekycMock,
        ComplianceMock complianceMock
) {

    public record Retry(
            @Min(1) @DefaultValue("3") int defaultMaxOcrRetries,
            @Min(1) @DefaultValue("3") int defaultMaxLivenessRetries,
            @Min(1) @DefaultValue("3") int defaultMaxNfcRetries
    ) {}

    public record Otp(
            @Min(4) @DefaultValue("6") int length,
            @Min(30) @DefaultValue("300") int ttlSeconds,
            @Min(1) @DefaultValue("5") int maxVerifyAttempts,
            @DefaultValue("true") boolean debugEndpointEnabled
    ) {}

    public record Dropoff(
            @Min(1) @DefaultValue("24") int ttlHours
    ) {}

    public record EkycMock(
            @DefaultValue("true") boolean alwaysPassByDefault
    ) {}

    public record ComplianceMock(
            @NotBlank @DefaultValue("RULE_BASED") String strategy
    ) {}
}