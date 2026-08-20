package com.bank.onboarding.controller;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.dto.IdentityAndOtpDtos.OtpDebugResponse;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint tiện debug: xem OTP hiện tại thay vì phải cắm SMS gateway thật.
 * BẮT BUỘC tắt (app.onboarding.otp.debug-endpoint-enabled=false) trước khi
 * lên môi trường có dữ liệu thật — mặc định chỉ nên bật ở dev/local.
 */
@RestController
@RequestMapping("/api/onboarding/debug")
@RequiredArgsConstructor
public class DebugController {

    private final OtpService otpService;
    private final OnboardingProperties properties;

    @GetMapping("/sessions/{sessionId}/otp")
    public OtpDebugResponse peekOtp(@PathVariable String sessionId) {
        if (!properties.getOtp().isDebugEndpointEnabled()) {
            throw new OnboardingException(HttpStatus.FORBIDDEN, "DEBUG_DISABLED",
                    "Debug OTP endpoint đang tắt (app.onboarding.otp.debug-endpoint-enabled=false)");
        }
        String otp = otpService.debugPeek(sessionId);
        if (otp == null) {
            throw OnboardingException.notFound("Chưa có OTP nào được gửi cho phiên này (hoặc đã hết hạn)");
        }
        return new OtpDebugResponse(sessionId, otp, otpService.ttlSecondsRemaining(sessionId));
    }
}
