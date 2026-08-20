package com.bank.onboarding.dto;

import jakarta.validation.constraints.NotBlank;

public class InitDtos {

    /** Phase 0: App vendor (chính chủ) mở SDK, không cần OAuth thật -> sinh accessToken nội bộ. */
    public record InitSessionRequest(
            @NotBlank String vendorId,
            @NotBlank String sdkSessionId,
            @NotBlank String productType
    ) {}

    public record InitSessionResponse(
            String sessionId,
            String accessToken,
            String phase
    ) {}

    /** Phase 1: kiểm tra thiết bị + hỗ trợ NFC. */
    public record DeviceCheckRequest(
            @NotBlank String model,
            boolean nfcSupported,
            String osVersion
    ) {}

    public record DeviceCheckResponse(
            boolean eligible,
            String reason,
            String phase
    ) {}

    private InitDtos() {}
}
