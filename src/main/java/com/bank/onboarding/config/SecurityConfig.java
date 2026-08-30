package com.bank.onboarding.config;

import com.bank.onboarding.security.VendorHmacAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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

      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                  .csrf(csrf -> csrf.disable()) // stateless server-to-server API, không dùng cookie
                  .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                  .authorizeHttpRequests(auth -> auth
                  .requestMatchers("/", "/*.html", "/*.jsx",
                                    "/actuator/health/**", "/docs/**", "/v3/api-docs/**").permitAll()
                  .requestMatchers("/api/onboarding/debug/**").permitAll() // tự guard bằng debug-endpoint-enabled
                  .requestMatchers("/api/conductor/**").hasRole("VENDOR")
                  .anyRequest().denyAll())
                  .addFilterBefore(vendorHmacAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
      }
}