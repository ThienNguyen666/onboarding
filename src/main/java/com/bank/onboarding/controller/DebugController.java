package com.bank.onboarding.controller;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.dto.IdentityAndOtpDtos.OtpDebugResponse;
import com.bank.onboarding.dto.SessionSummary;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.service.ConductorSessionAdminService;
import com.bank.onboarding.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ConductorSessionAdminService adminService; 
    
    @GetMapping("/sessions")
    public java.util.List<SessionSummary> listSessions() {
        guardDebugEnabled();
        return adminService.listRecentSessions();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        guardDebugEnabled();
        adminService.resetAllData();
        log.warn("[DEBUG] Reset all data requested");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/otp")
    public OtpDebugResponse peekOtp(@RequestParam String phone) {
        if (!properties.otp().debugEndpointEnabled()) {
                throw new OnboardingException(HttpStatus.FORBIDDEN, "DEBUG_DISABLED",
                        "Debug OTP endpoint đang tắt");
        }
        String key = "conductor:" + phone;
        String otp = otpService.debugPeek(key);
        if (otp == null) {
                throw OnboardingException.notFound("Chưa có OTP nào gửi cho SĐT này (hoặc đã hết hạn)");
        }
        return new OtpDebugResponse(key, otp, otpService.ttlSecondsRemaining(key));
    }

    private void guardDebugEnabled() {
        if (!properties.otp().debugEndpointEnabled()) {
            throw new OnboardingException(HttpStatus.FORBIDDEN, "DEBUG_DISABLED",
                    "Debug endpoints đang tắt (app.onboarding.otp.debug-endpoint-enabled=false)");
        }
    }
}
