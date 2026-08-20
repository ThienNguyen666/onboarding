package com.bank.onboarding.repository;

import com.bank.onboarding.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {
}
