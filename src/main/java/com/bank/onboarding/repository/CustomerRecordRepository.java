package com.bank.onboarding.repository;

import com.bank.onboarding.entity.CustomerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRecordRepository extends JpaRepository<CustomerRecord, String> {
    Optional<CustomerRecord> findByPhone(String phone);
}
