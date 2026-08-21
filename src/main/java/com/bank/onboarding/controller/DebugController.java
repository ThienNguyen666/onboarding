package com.bank.onboarding.controller;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.dto.IdentityAndOtpDtos.OtpDebugResponse;
import com.bank.onboarding.dto.SessionSummary;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.service.OnboardingOrchestrationService;
import com.bank.onboarding.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

/**
 * Endpoint tiện debug: xem OTP hiện tại thay vì phải cắm SMS gateway thật.
 * BẮT BUỘC tắt (app.onboarding.otp.debug-endpoint-enabled=false) trước khi
 * lên môi trường có dữ liệu thật — mặc định chỉ nên bật ở dev/local.
 */
@Slf4j 
@RestController
@RequestMapping("/api/onboarding/debug")
@RequiredArgsConstructor
public class DebugController {

    private final OtpService otpService;
    private final OnboardingProperties properties;
    private final OnboardingOrchestrationService orchestrationService; // MỚI

    @GetMapping("/sessions/{sessionId}/otp")
    public OtpDebugResponse peekOtp(@PathVariable String sessionId) {
        if (!properties.otp().debugEndpointEnabled()) {          // đổi từ getOtp().isDebugEndpointEnabled()
            throw new OnboardingException(HttpStatus.FORBIDDEN, "DEBUG_DISABLED",
                    "Debug OTP endpoint đang tắt (app.onboarding.otp.debug-endpoint-enabled=false)");
        }
        log.warn("[DEBUG] OTP peek requested for session={} — CHỈ được bật ở dev/local!", sessionId);
        String otp = otpService.debugPeek(sessionId);
        if (otp == null) {
            throw OnboardingException.notFound("Chưa có OTP nào được gửi cho phiên này (hoặc đã hết hạn)");
        }
        return new OtpDebugResponse(sessionId, otp, otpService.ttlSecondsRemaining(sessionId));
    }

    @GetMapping("/sessions")
    public java.util.List<SessionSummary> listSessions() {
        guardDebugEnabled();
        return orchestrationService.listRecentSessions();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        guardDebugEnabled();
        orchestrationService.resetAllData();
        log.warn("[DEBUG] Reset all data requested");
        return ResponseEntity.noContent().build();
    }

    private void guardDebugEnabled() {
        if (!properties.otp().debugEndpointEnabled()) {
            throw new OnboardingException(HttpStatus.FORBIDDEN, "DEBUG_DISABLED",
                    "Debug endpoints đang tắt (app.onboarding.otp.debug-endpoint-enabled=false)");
        }
    }
}
