package com.bank.onboarding.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Sau khi chuyển state machine sang Orkes Conductor, entity này KHÔNG còn giữ
 * phase/retry-count/otpVerified... — Orkes workflow instance là nguồn sự thật
 * duy nhất cho state (query qua WorkflowClient.getWorkflow()). Entity chỉ còn
 * vai trò bảng mapping mỏng phone -> workflowId mới nhất, phục vụ:
 *  - FE tra lại workflowId khi mất (refresh trang / đổi thiết bị)
 *  - check_dropoff worker tìm workflow cũ theo phone
 *  - QA Console liệt kê session gần đây (DebugController)
 */
@Entity
@Table(name = "onboarding_session",
       indexes = @Index(name = "idx_session_phone_created", columnList = "phone, createdAt"))
@Getter
@Setter
@NoArgsConstructor
public class OnboardingSession {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, unique = true)
    private String workflowId;

    @Column(nullable = false)
    private String vendorId;

    private String phone;

    /**
     * Cache trạng thái cuối nhận từ Orkes — CHỈ để hiển thị nhanh trong QA
     * Console/debug list, KHÔNG dùng để quyết định business logic (mọi
     * quyết định luồng phải đọc trực tiếp từ Workflow.getStatus()).
     * Được set bởi audit_log_final_result worker khi workflow kết thúc.
     */
    private String lastKnownStatus;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public OnboardingSession(String workflowId, String vendorId, String phone) {
        this.workflowId = workflowId;
        this.vendorId = vendorId;
        this.phone = phone;
    }
}