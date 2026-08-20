package com.bank.onboarding.dto;

import java.util.Map;

public class EkycStepDtos {

    /**
     * Request dùng chung cho OCR/Liveness/NFC mock. `forceFail` cho phép QA/demo
     * chủ động tạo case fail để test retry & termination mà không cần vendor thật.
     * `mockPayload` cho phép FE gửi kèm dữ liệu giả (VD: số CCCD, họ tên) để hiển thị lại.
     */
    public record EkycStepRequest(
            boolean forceFail,
            Map<String, Object> mockPayload
    ) {}

    public record EkycStepResponse(
            boolean passed,
            int attempt,
            int maxRetries,
            boolean retryAllowed,
            String phase,
            String status // IN_PROGRESS | FAILED (khi hết lượt thử)
    ) {}

    private EkycStepDtos() {}
}
