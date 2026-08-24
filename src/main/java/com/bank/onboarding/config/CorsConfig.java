package com.bank.onboarding.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cho phép FE demo (chạy khác origin với backend) gọi thẳng REST API.
 * CHỈ mở "*" ở dev/local — lên môi trường thật phải whitelist domain
 * cụ thể của app vendor, không để allowedOriginPatterns("*").
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}