package com.bank.onboarding.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI TYPE_BASE = URI.create("https://api.vietbank.example/errors/");

    @ExceptionHandler(OnboardingException.class)
    public ProblemDetail handle(OnboardingException ex) {
        log.warn("Business error [{}]: {}", ex.getCode(), ex.getMessage());
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", message);
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadInput(Exception ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource không tồn tại");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau");
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(TYPE_BASE.resolve(code.toLowerCase().replace('_', '-')));
        pd.setTitle(code);
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}