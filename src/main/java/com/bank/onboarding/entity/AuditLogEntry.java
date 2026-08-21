package com.bank.onboarding.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLogEntry(String sessionId, String event, Map<String, Object> detail) {
        this.sessionId = sessionId;
        this.event = event;
        this.detail = detail;
    }
}