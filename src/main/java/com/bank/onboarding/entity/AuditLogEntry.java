package com.bank.onboarding.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Ghi vết cuối luồng (và các mốc quan trọng) — tương ứng task
 * "audit_log_final_result" trong workflow gốc.
 */
@Entity
@Table(name = "audit_log_entry")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntry {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String event;

    @Lob
    private String detailJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLogEntry(String sessionId, String event, String detailJson) {
        this.sessionId = sessionId;
        this.event = event;
        this.detailJson = detailJson;
    }
}
