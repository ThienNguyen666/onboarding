package com.bank.onboarding.controller;

import com.bank.onboarding.dto.AccountCreationDtos.CreateAccountRequest;
import com.bank.onboarding.dto.AccountCreationDtos.CreateAccountResponse;
import com.bank.onboarding.dto.CustomerLookupDtos.CustomerLookupResponse;
import com.bank.onboarding.dto.CustomerLookupDtos.PhoneRequest;
import com.bank.onboarding.dto.EkycStepDtos.EkycStepRequest;
import com.bank.onboarding.dto.EkycStepDtos.EkycStepResponse;
import com.bank.onboarding.dto.IdentityAndOtpDtos.*;
import com.bank.onboarding.dto.InitDtos.*;
import com.bank.onboarding.dto.SessionStatusResponse;
import com.bank.onboarding.service.OnboardingOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API cho App Vendor (mobile bank) gọi tuần tự theo từng Phase của luồng
 * eKYC — xem README.md để biết thứ tự gọi đầy đủ.
 */
@RestController
@RequestMapping("/api/onboarding/sessions")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingOrchestrationService service;

    // Phase 0
    @PostMapping
    public ResponseEntity<InitSessionResponse> init(@Valid @RequestBody InitSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.init(req));
    }

    // Phase 1
    @PostMapping("/{sessionId}/device-check")
    public DeviceCheckResponse deviceCheck(@PathVariable String sessionId, @Valid @RequestBody DeviceCheckRequest req) {
        return service.checkDevice(sessionId, req);
    }

    // Phase 2
    @PostMapping("/{sessionId}/customer-lookup")
    public CustomerLookupResponse customerLookup(@PathVariable String sessionId, @Valid @RequestBody PhoneRequest req) {
        return service.lookupCustomer(sessionId, req);
    }

    // Phase 3
    @PostMapping("/{sessionId}/ocr")
    public EkycStepResponse ocr(@PathVariable String sessionId, @RequestBody(required = false) EkycStepRequest req) {
        return service.submitOcr(sessionId, orEmpty(req));
    }

    // Phase 4
    @PostMapping("/{sessionId}/liveness")
    public EkycStepResponse liveness(@PathVariable String sessionId, @RequestBody(required = false) EkycStepRequest req) {
        return service.submitLiveness(sessionId, orEmpty(req));
    }

    // Phase 5
    @PostMapping("/{sessionId}/nfc")
    public EkycStepResponse nfc(@PathVariable String sessionId, @RequestBody(required = false) EkycStepRequest req) {
        return service.submitNfc(sessionId, orEmpty(req));
    }

    // Phase 6a
    @PostMapping("/{sessionId}/identity-confirm")
    public IdentityConfirmResponse identityConfirm(@PathVariable String sessionId) {
        return service.confirmIdentity(sessionId);
    }

    // Phase 6b
    @PostMapping("/{sessionId}/tnc")
    public ResponseEntity<Void> tnc(@PathVariable String sessionId, @Valid @RequestBody TncAcceptRequest req) {
        service.acceptTnc(sessionId, req);
        return ResponseEntity.noContent().build();
    }

    // Phase 6c
    @PostMapping("/{sessionId}/otp/send")
    public OtpSendResponse sendOtp(@PathVariable String sessionId) {
        return service.sendOtp(sessionId);
    }

    @PostMapping("/{sessionId}/otp/verify")
    public OtpVerifyResponse verifyOtp(@PathVariable String sessionId, @Valid @RequestBody OtpVerifyRequest req) {
        return service.verifyOtp(sessionId, req);
    }

    // Phase 7
    @PostMapping("/{sessionId}/account")
    public CreateAccountResponse createAccount(@PathVariable String sessionId,
                                                @RequestBody(required = false) CreateAccountRequest req) {
        return service.createAccount(sessionId, req == null ? new CreateAccountRequest(null) : req);
    }

    // Query chung / FE polling
    @GetMapping("/{sessionId}")
    public SessionStatusResponse status(@PathVariable String sessionId) {
        return service.status(sessionId);
    }

    // KH thoát app giữa chừng -> FE gọi để bật cờ dropoff cho lần sau
    @PostMapping("/{sessionId}/dropoff")
    public ResponseEntity<Void> markDropoff(@PathVariable String sessionId) {
        service.markDropoff(sessionId);
        return ResponseEntity.accepted().build();
    }

    private EkycStepRequest orEmpty(EkycStepRequest req) {
        return req == null ? new EkycStepRequest(false, null) : req;
    }
}
