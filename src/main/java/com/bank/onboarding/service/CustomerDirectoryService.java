package com.bank.onboarding.service;

import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.entity.CustomerRecord;
import com.bank.onboarding.repository.CustomerRecordRepository;
import com.bank.onboarding.util.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerDirectoryService {

    public record DropoffInfo(String sdkSessionId, String resumeStep) {}

    private final CustomerRecordRepository customerRecordRepository;
    private final StringRedisTemplate redisTemplate;
    private final OnboardingProperties properties;

    private String dropoffKey(String phone) { 
        return "onboarding:dropoff:" + phone; 
    }

    @Transactional(readOnly = true)
    public CustomerType lookupType(String phone) {
        return customerRecordRepository.findByPhone(phone).isPresent() ? CustomerType.ETB : CustomerType.NTB;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerRecord> findEtb(String phone) {
        return customerRecordRepository.findByPhone(phone);
    }

    public String newNtbCustomerId() {
        return "NTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional
    public void registerAsEtbIfAbsent(String customerId, String phone, String fullName) {
        if (phone == null || customerRecordRepository.findByPhone(phone).isPresent()) {
            return; // đã ETB rồi hoặc thiếu phone -> bỏ qua
        }
        try {
            customerRecordRepository.save(new CustomerRecord(customerId, phone, fullName));
            log.info("Customer {} (phone={}) chuyển NTB -> ETB sau khi mở TK SUCCESS", customerId, Masking.phone(phone));
        } catch (DataIntegrityViolationException e) {
            log.debug("registerAsEtbIfAbsent race-condition, bỏ qua phone={}", Masking.phone(phone));
        }
    }
    
    public void markDropoff(String phone, String sessionId, String resumeStep) {
        redisTemplate.opsForValue().set(
                dropoffKey(phone),
                sessionId + "|" + resumeStep,
                Duration.ofHours(properties.dropoff().ttlHours()));
        log.info("Marked dropoff phone={} session={} resumeStep={}", Masking.phone(phone), sessionId, resumeStep);
    }

    public void clearDropoff(String phone) {
        redisTemplate.delete(dropoffKey(phone));
    }

    public Optional<DropoffInfo> findDropoff(String phone) {
        String raw = redisTemplate.opsForValue().get(dropoffKey(phone));
        if (raw == null) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 2);
        return Optional.of(new DropoffInfo(parts[0], parts[1]));
    }
}