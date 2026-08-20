package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.entity.CustomerRecord;
import com.bank.onboarding.repository.CustomerRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    private final CustomerRecordRepository customerRecordRepository;
    private final StringRedisTemplate redisTemplate;
    private final OnboardingProperties properties;

    private String dropoffKey(String phone) { return "onboarding:dropoff:" + phone; }

    public CustomerType lookupType(String phone) {
        return customerRecordRepository.findByPhone(phone).isPresent() ? CustomerType.ETB : CustomerType.NTB;
    }

    public Optional<CustomerRecord> findEtb(String phone) {
        return customerRecordRepository.findByPhone(phone);
    }

    public String newNtbCustomerId() {
        return "NTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Đánh dấu 1 phiên NTB đang dở dang để lần sau quay lại có thể resume. */
    public void markDropoff(String phone, String sessionId, String resumeStep) {
        redisTemplate.opsForValue().set(
                dropoffKey(phone),
                sessionId + "|" + resumeStep,
                Duration.ofHours(properties.getDropoff().getTtlHours()));
    }

    public void clearDropoff(String phone) {
        redisTemplate.delete(dropoffKey(phone));
    }

    /** Trả về {sessionId, resumeStep} nếu có phiên dropoff còn hiệu lực. */
    public Optional<String[]> findDropoff(String phone) {
        String raw = redisTemplate.opsForValue().get(dropoffKey(phone));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(raw.split("\\|", 2));
    }
}
