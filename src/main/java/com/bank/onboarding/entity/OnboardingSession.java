package com.bank.onboarding.entity;

import com.bank.onboarding.domain.ComplianceStatus;
import com.bank.onboarding.domain.CustomerType;
import com.bank.onboarding.domain.SessionPhase;
import com.bank.onboarding.domain.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Thay thế cho "workflow instance" của Orkes trong bản prototype này:
 * mỗi phiên mở tài khoản NTB qua SDK vendor là 1 row, cập nhật dần qua
 * từng phase. Các trường *Data lưu JSON string (mock payload OCR/Liveness/NFC)
 * để tránh phụ thuộc thêm thư viện JSON-column.
 */
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

    // ---- Phase 1 ----
    @Lob
    private String deviceInfoJson;
    private Boolean deviceEligible;

    // ---- Phase 2 ----
    private String phone;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private CustomerType customerType;
    private boolean dropoff = false;

    // ---- Phase 3: OCR ----
    @Lob
    private String cccdDataJson;
    private int ocrRetryCount = 0;
    private Boolean ocrPassed;

    // ---- Phase 4: Liveness ----
    @Lob
    private String livenessDataJson;
    private int livenessRetryCount = 0;
    private Boolean livenessPassed;

    // ---- Phase 5: NFC ----
    @Lob
    private String nfcDataJson;
    private int nfcRetryCount = 0;
    private Boolean nfcPassed;

    // ---- Phase 6: identity / TnC / OTP ----
    private boolean identityConfirmed = false;
    private boolean tncAccepted = false;
    private String otpTokenRef; // trỏ tới OTP record trong Redis, không lưu OTP thật ở đây
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
