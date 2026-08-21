package com.bank.onboarding.dto;

/** Response chuẩn cho mọi lỗi API (thay Map<String,Object> để type-safe). */
public record ErrorResponse(String timestamp, String code, String message) {}