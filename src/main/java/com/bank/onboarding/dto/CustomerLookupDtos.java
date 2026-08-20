package com.bank.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CustomerLookupDtos {

    public record PhoneRequest(
            @NotBlank @Pattern(regexp = "0\\d{9}", message = "SĐT không hợp lệ (10 số, bắt đầu bằng 0)")
            String phone
    ) {}

    public record CustomerLookupResponse(
            String customerType,     // ETB | NTB
            boolean dropoff,
            String resumeStep,       // phase để FE điều hướng nếu dropoff = true
            String phase
    ) {}

    private CustomerLookupDtos() {}
}
