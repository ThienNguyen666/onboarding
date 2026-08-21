package com.bank.onboarding.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OnboardingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public OnboardingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static OnboardingException notFound(String message) {
        return new OnboardingException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", message);
    }

    public static OnboardingException badState(String message) {
        return new OnboardingException(HttpStatus.CONFLICT, "INVALID_PHASE", message);
    }

    public static OnboardingException badRequest(String message) {
        return new OnboardingException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}