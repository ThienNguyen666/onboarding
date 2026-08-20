package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mọi lệnh gọi vendor thật (OCR / Liveness / NFC / C06) được thay bằng mock
 * data cố định + cờ forceFail để dev/QA chủ động test nhánh fail & retry mà
 * không cần tích hợp vendor.
 */
@Service
@RequiredArgsConstructor
public class MockEkycService {

    private final ObjectMapper objectMapper;
    private final OnboardingProperties properties;

    public boolean decidePassed(boolean forceFail) {
        if (forceFail) {
            return false;
        }
        return properties.getEkycMock().isAlwaysPassByDefault();
    }

    @SneakyThrows
    public String mockCccdData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idNumber", "079099001234");
        data.put("fullName", "NGUYEN VAN A");
        data.put("dob", LocalDate.now().minusYears(25).toString());
        data.put("gender", "MALE");
        data.put("address", "123 Nguyen Trai, Q1, TP.HCM");
        data.put("issueDate", LocalDate.now().minusYears(3).toString());
        data.put("expiryDate", LocalDate.now().plusYears(12).toString());
        if (overrides != null) {
            data.putAll(overrides);
        }
        return objectMapper.writeValueAsString(data);
    }

    @SneakyThrows
    public String mockLivenessData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("livenessScore", 0.97);
        data.put("matchWithCccdPhoto", true);
        data.put("checkedAt", java.time.Instant.now().toString());
        if (overrides != null) {
            data.putAll(overrides);
        }
        return objectMapper.writeValueAsString(data);
    }

    @SneakyThrows
    public String mockNfcData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chipVerified", true);
        data.put("c06Called", false); // mock: coi như luôn có sẵn cache dân cư
        data.put("nationalDbMatch", true);
        if (overrides != null) {
            data.putAll(overrides);
        }
        return objectMapper.writeValueAsString(data);
    }

    @SneakyThrows
    public int ageFromCccd(String cccdDataJson) {
        Map<?, ?> map = objectMapper.readValue(cccdDataJson, Map.class);
        LocalDate dob = LocalDate.parse((String) map.get("dob"));
        return Period.between(dob, LocalDate.now()).getYears();
    }
}
