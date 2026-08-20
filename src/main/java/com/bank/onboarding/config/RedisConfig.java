package com.bank.onboarding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis ở đây chỉ dùng cho dữ liệu "sống ngắn hạn": OTP (tự hết hạn theo TTL)
 * và cache dropoff-by-phone để show lại đúng màn hình khi KH quay lại app.
 * Toàn bộ state chính thức của phiên vẫn nằm ở Postgres (OnboardingSession).
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
