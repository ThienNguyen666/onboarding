package com.bank.onboarding.config;

import com.bank.onboarding.security.VendorHmacAuthenticationFilter;
import com.bank.onboarding.security.VendorRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

      private final VendorHmacAuthenticationFilter vendorHmacAuthenticationFilter;
      private final VendorRateLimitFilter vendorRateLimitFilter;

      // cùng property key với filter — 2 nơi phải đồng bộ, trước đây SecurityConfig
      // "hardcode" hasRole(VENDOR) bất kể cờ này, gây 403 khi security.enabled=false.
      @Value("${app.onboarding.security.enabled:true}")
      private boolean securityEnabled;

      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                  .csrf(csrf -> csrf.disable())
                  .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                  .authorizeHttpRequests(auth -> {
                        auth.requestMatchers("/", "/*.html", "/*.jsx","/*.ico",
                                          "/actuator/health/**", "/docs/**", "/v3/api-docs/**").permitAll()
                            .requestMatchers("/api/onboarding/debug/**").permitAll();                        
                        if (securityEnabled) {
                              auth.requestMatchers("/api/conductor/**").hasRole("VENDOR");
                        } else {.
                              auth.requestMatchers("/api/conductor/**").permitAll();
                        }
                        auth.anyRequest().denyAll();
                  })
		  .addFilterBefore(vendorRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
		  .addFilterBefore(vendorHmacAuthenticationFilter, VendorRateLimitFilter.class);
            return http.build();
      }
}