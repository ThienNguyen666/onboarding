package com.bank.onboarding.entity;

import com.bank.onboarding.domain.ComplianceStatus;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.domain.SessionPhase;
import com.bank.onboarding.domain.SessionStatus;
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
@Table(name = "onboarding_session")
@Getter
@Setter
@NoArgsConstructor
public class OnboardingSession {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    // ---- Phase 0 ----
    @Column(nullable = false)
    private String vendorId;
    private String sdkSessionId;
    private String productType;
    private String accessToken;

    // ---- Phase 1 ---- (đơn giản hoá: 3 field DeviceCheckRequest, không cần blob JSON)
    private String deviceModel;
    private String deviceOsVersion;
    private Boolean deviceNfcSupported;
    private Boolean deviceEligible;

    // ---- Phase 2 ----
    private String phone;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private CustomerType customerType;
    private boolean dropoff = false;

    // ---- Phase 3: OCR ----
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> cccdData;
    private int ocrRetryCount = 0;
    private Boolean ocrPassed;

    // ---- Phase 4: Liveness ----
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> livenessData;
    private int livenessRetryCount = 0;
    private Boolean livenessPassed;

    // ---- Phase 5: NFC ----
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> nfcData;
    private int nfcRetryCount = 0;
    private Boolean nfcPassed;

    // ---- Phase 6: identity / TnC / OTP ----
    private boolean identityConfirmed = false;
    private boolean tncAccepted = false;
    private String otpTokenRef;
    private boolean otpVerified = false;

    // ---- Phase 7: account creation ----
    private String ebankUserId;
    private String accountNumber;
    private String linkId;
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus;
    private String failureReason;

    // ---- state machine bookkeeping ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionPhase phase = SessionPhase.INIT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    private String terminationReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void terminate(SessionStatus status, String reason) {
        this.status = status;
        this.terminationReason = reason;
        this.phase = SessionPhase.DONE;
    }
}