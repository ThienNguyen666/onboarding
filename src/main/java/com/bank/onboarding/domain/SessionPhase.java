package com.bank.onboarding.domain;

/**
 * Ánh xạ 1-1 với các "Phase" mô tả trong workflow Orkes
 * (vendor_sdk_ekyc_account_opening_2.json) — dùng để FE biết đang ở bước nào
 * và để resume dropoff.
 */
public enum SessionPhase {
    INIT,                 // Phase 0 - lấy AccessToken, show CVP
    DEVICE_CHECK,          // Phase 1
    CUSTOMER_LOOKUP,        // Phase 2 - check ETB/NTB + dropoff
    OCR,                   // Phase 3
    LIVENESS,               // Phase 4
    NFC,                    // Phase 5
    IDENTITY_CONFIRM,        // Phase 6a
    TNC,                    // Phase 6b
    OTP,                    // Phase 6c
    ACCOUNT_CREATION,        // Phase 7
    DONE
}
