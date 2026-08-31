package com.bank.onboarding.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit đơn giản theo X-Vendor-Id (fallback IP nếu chưa xác thực).
 * In-memory (đủ cho single-instance prototype) — 60 req/phút/vendor.
 */
@Component
public class VendorRateLimitFilter extends OncePerRequestFilter {

      private static final int LIMIT_PER_MINUTE = 60;
      private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/conductor");
      }

      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                  throws ServletException, IOException {
            String key = request.getHeader("X-Vendor-Id");
            if (key == null || key.isBlank()) key = request.getRemoteAddr();

            Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                        .addLimit(Bandwidth.simple(LIMIT_PER_MINUTE, Duration.ofMinutes(1)))
                        .build());

            if (bucket.tryConsume(1)) {
                  chain.doFilter(request, response);
            } else {
                  response.setStatus(429);
                  response.setContentType("application/json");
                  response.getWriter().write(
                        "{\"code\":\"RATE_LIMITED\",\"detail\":\"Quá nhiều request, vui lòng thử lại sau 1 phút\"}");
            }
      }
}