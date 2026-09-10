package com.bank.onboarding.security;

import com.bank.onboarding.config.OnboardingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Xác thực vendor bằng HMAC-SHA256:
 *   X-Vendor-Id: <vendorId>
 *   X-Timestamp:  <epoch giây>
 *   X-Signature:  hex(HMAC_SHA256(secret, vendorId + "." + timestamp + "." + rawBody))
 * Chống replay bằng cửa sổ ±300s.
 */
@Component
@RequiredArgsConstructor
public class VendorHmacAuthenticationFilter extends OncePerRequestFilter {

      private static final long ALLOWED_SKEW_SECONDS = 300;

      private final OnboardingProperties properties;

      @Value("${app.onboarding.security.enabled:true}")
      private boolean securityEnabled;

      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
            return !securityEnabled || !request.getRequestURI().startsWith("/api/conductor");
      }

      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                          FilterChain chain) throws ServletException, IOException {
            
            // Wrap request bằng ContentCachingRequestWrapper
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 10000);

            // Đọc trực tiếp byte stream từ InputStream của request để nạp vào cache
            byte[] bodyBytes = wrappedRequest.getInputStream().readAllBytes();

            String vendorId = wrappedRequest.getHeader("X-Vendor-Id");
            String timestamp = wrappedRequest.getHeader("X-Timestamp");
            String signature = wrappedRequest.getHeader("X-Signature");

            if (vendorId == null || timestamp == null || signature == null) {
                  response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Thiếu header xác thực vendor");
                  return;
            }

            OnboardingProperties.Vendor vendor = properties.vendors() == null ? null : properties.vendors().get(vendorId);
            if (vendor == null) {
                  response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Vendor không hợp lệ");
                  return;
            }

            long ts;
            try {
                  ts = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                  response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Timestamp không hợp lệ");
                  return;
            }
            
            if (Math.abs(Instant.now().getEpochSecond() - ts) > ALLOWED_SKEW_SECONDS) {
                  response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Request hết hạn (chống replay)");
                  return;
            }

            // Lấy raw body đã đọc từ stream để tính HMAC signature
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            String payload = vendorId + "." + timestamp + "." + body;
            String expected = hmacSha256Hex(vendor.secret(), payload);

            if (!constantTimeEquals(expected, signature)) {
                  response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Chữ ký không hợp lệ");
                  return;
            }

            var auth = new UsernamePasswordAuthenticationToken(vendorId, null, List.of(() -> "ROLE_VENDOR"));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(wrappedRequest));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Chuyển wrappedRequest tiếp vào filter chain để các Controller downstream có thể đọc lại body
            chain.doFilter(wrappedRequest, response);
      }

      private String hmacSha256Hex(String secret, String data) {
            try {
                  Mac mac = Mac.getInstance("HmacSHA256");
                  mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                  return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                  throw new IllegalStateException("Không tính được HMAC", e);
            }
      }

      private boolean constantTimeEquals(String a, String b) {
            return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
      }
}