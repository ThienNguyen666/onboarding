package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.exception.OnboardingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final OnboardingProperties properties;

    private String codeKey(String sessionId) { return "onboarding:otp:code:" + sessionId; }
    private String attemptsKey(String sessionId) { return "onboarding:otp:attempts:" + sessionId; }
    private String verifiedKey(String sessionId) { return "onboarding:otp:verified:" + sessionId; }
    public static String workflowSessionKey(String phone) {
        return "conductor:" + phone;
    }
    public String generateAndStore(String sessionId) {
        int length = properties.otp().length();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        String otp = sb.toString();
        Duration ttl = Duration.ofSeconds(properties.otp().ttlSeconds());
        redisTemplate.opsForValue().set(codeKey(sessionId), otp, ttl);
        redisTemplate.delete(attemptsKey(sessionId));
        redisTemplate.delete(verifiedKey(sessionId));
        log.info("OTP generated for session={} (length={}, ttl={}s)", sessionId, length, ttl.getSeconds());
        return otp; // KHÔNG log giá trị OTP thật ra log, chỉ gửi qua kênh SMS/OTT mock.
    }

    public boolean verify(String sessionId, String candidate) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey(sessionId)))) {
            log.info("OTP verify session={} — idempotent retry, already verified", sessionId);
            return true;
        }

        String stored = redisTemplate.opsForValue().get(codeKey(sessionId));
        if (stored == null) {
            throw OnboardingException.badRequest("OTP đã hết hạn hoặc chưa được gửi, vui lòng gửi lại OTP");
        }

        long attempts = redisTemplate.opsForValue().increment(attemptsKey(sessionId), 1);
        redisTemplate.expire(attemptsKey(sessionId), Duration.ofSeconds(properties.otp().ttlSeconds()));

        if (attempts > properties.otp().maxVerifyAttempts()) {
            redisTemplate.delete(codeKey(sessionId));
            log.warn("OTP verify attempts exceeded for session={}", sessionId);
            throw OnboardingException.badRequest("Vượt quá số lần nhập OTP cho phép, vui lòng gửi lại OTP");
        }

        boolean matched = stored.equals(candidate);
        if (matched) {
            redisTemplate.delete(codeKey(sessionId));
            redisTemplate.opsForValue().set(verifiedKey(sessionId), "1",
                    Duration.ofSeconds(properties.otp().ttlSeconds()));
        }
        log.info("OTP verify session={} result={}", sessionId, matched ? "MATCH" : "MISMATCH");
        return matched;
    }

    public int attemptsLeft(String sessionId) {
        String raw = redisTemplate.opsForValue().get(attemptsKey(sessionId));
        int used = raw == null ? 0 : Integer.parseInt(raw);
        return Math.max(0, properties.otp().maxVerifyAttempts() - used);
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