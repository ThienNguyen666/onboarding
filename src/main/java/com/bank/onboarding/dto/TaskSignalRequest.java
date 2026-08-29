package com.bank.onboarding.dto;

import java.util.Map;

/**
 * Payload FE gửi khi KH hoàn thành 1 bước cần asyncComplete (OCR/Liveness/
 * NFC/OTP verify). forceFail chỉ dùng cho QA/demo ép fail để test retry loop.
 */
public record TaskSignalRequest(
        boolean forceFail,
        Map<String, Object> outputData
) {}