package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.exception.OnboardingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OTP lưu ở Redis (tự hết hạn theo TTL, không cần job dọn dẹp).
 * Để tiện debug/demo, có endpoint riêng (xem DebugController) đọc lại OTP
 * hiện tại thay vì phải cắm SMS gateway thật.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final OnboardingProperties properties;

    private String codeKey(String sessionId) { return "onboarding:otp:code:" + sessionId; }
    private String attemptsKey(String sessionId) { return "onboarding:otp:attempts:" + sessionId; }

    public String generateAndStore(String sessionId) {
        int length = properties.getOtp().getLength();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        String otp = sb.toString();
        Duration ttl = Duration.ofSeconds(properties.getOtp().getTtlSeconds());
        redisTemplate.opsForValue().set(codeKey(sessionId), otp, ttl);
        redisTemplate.delete(attemptsKey(sessionId));
        return otp;
    }

    public boolean verify(String sessionId, String candidate) {
        String stored = redisTemplate.opsForValue().get(codeKey(sessionId));
        if (stored == null) {
            throw OnboardingException.badRequest("OTP đã hết hạn hoặc chưa được gửi, vui lòng gửi lại OTP");
        }
        long attempts = redisTemplate.opsForValue().increment(attemptsKey(sessionId), 1);
        redisTemplate.expire(attemptsKey(sessionId), Duration.ofSeconds(properties.getOtp().getTtlSeconds()));

        if (attempts > properties.getOtp().getMaxVerifyAttempts()) {
            redisTemplate.delete(codeKey(sessionId));
            throw OnboardingException.badRequest("Vượt quá số lần nhập OTP cho phép, vui lòng gửi lại OTP");
        }

        boolean matched = stored.equals(candidate);
        if (matched) {
            redisTemplate.delete(codeKey(sessionId));
        }
        return matched;
    }

    public int attemptsLeft(String sessionId) {
        String raw = redisTemplate.opsForValue().get(attemptsKey(sessionId));
        int used = raw == null ? 0 : Integer.parseInt(raw);
        return Math.max(0, properties.getOtp().getMaxVerifyAttempts() - used);
    }

    /** CHỈ dùng cho debug/dev — xem OTP hiện tại thay vì gửi SMS thật. */
    public String debugPeek(String sessionId) {
        return redisTemplate.opsForValue().get(codeKey(sessionId));
    }

    public long ttlSecondsRemaining(String sessionId) {
        Long ttl = redisTemplate.getExpire(codeKey(sessionId), TimeUnit.SECONDS);
        return ttl == null ? -1 : ttl;
    }
}
