package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.exception.OnboardingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mọi lệnh gọi vendor thật (OCR / Liveness / NFC / C06) được thay bằng mock
 * data cố định + cờ forceFail để dev/QA chủ động test nhánh fail & retry.
 */
@Service
@RequiredArgsConstructor
public class MockEkycService {

    private final OnboardingProperties properties;

    public boolean decidePassed(boolean forceFail) {
        return !forceFail && properties.ekycMock().alwaysPassByDefault();
    }

    public Map<String, Object> mockCccdData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idNumber", "079099001234");
        data.put("fullName", "NGUYEN VAN A");
        data.put("dob", LocalDate.now().minusYears(25).toString());
        data.put("gender", "MALE");
        data.put("address", "Hai Ba Trung, Phuong Sai Gon, TP.HCM");
        data.put("issueDate", LocalDate.now().minusYears(3).toString());
        data.put("expiryDate", LocalDate.now().plusYears(12).toString());
        if (overrides != null) data.putAll(overrides);
        return data;
    }

    public Map<String, Object> mockLivenessData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("livenessScore", 0.97);
        data.put("matchWithCccdPhoto", true);
        data.put("checkedAt", Instant.now().toString());
        if (overrides != null) data.putAll(overrides);
        return data;
    }

    public Map<String, Object> mockNfcData(Map<String, Object> overrides) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chipVerified", true);
        data.put("c06Called", false); // mock: coi như luôn có sẵn cache dân cư
        data.put("nationalDbMatch", true);
        if (overrides != null) data.putAll(overrides);
        return data;
    }

    public int ageFromCccd(Map<String, Object> cccdData) {
        if (cccdData == null || cccdData.get("dob") == null) {
            throw OnboardingException.badState("Chưa có dữ liệu CCCD (OCR chưa hoàn tất) để xác định tuổi");
        }
        LocalDate dob = LocalDate.parse(String.valueOf(cccdData.get("dob")));
        return Period.between(dob, LocalDate.now()).getYears();
    }
}