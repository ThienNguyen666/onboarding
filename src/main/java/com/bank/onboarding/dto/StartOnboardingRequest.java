package com.bank.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Thay cho Map<String,Object> thô trước đây — có validate rõ ràng, trả 400 thay vì NPE/500. */
public record StartOnboardingRequest(
      @NotBlank String vendorClientId,
      @NotBlank String vendorClientSecret,
      @NotBlank String sdkSessionId,
      @NotBlank String productType,
      @NotNull @Valid DeviceInfo deviceInfo,
      @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "phone phải là SĐT VN 10 số, bắt đầu bằng 0")
      String phone,
      @NotBlank String vendorId,
      /** QA/demo override — sẽ bị bỏ qua nếu debug-endpoint-enabled=false (guard ở service). */
      String forceComplianceResult
) {
      public record DeviceInfo(String model, String osVersion, boolean nfcSupported) {}
}