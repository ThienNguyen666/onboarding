package com.bank.onboarding.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Bảng khách hàng hiện hữu (ETB) — dùng để mock bước "check_customer_by_phone".
 * Trong thực tế đây sẽ là core banking / CIF; ở prototype ta seed sẵn vài SĐT
 * để demo nhánh ETB.
 */
@Entity
@Table(name = "customer_record", indexes = @Index(name = "ix_customer_phone", columnList = "phone", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class CustomerRecord {

    @Id
    private String customerId;

    @Column(nullable = false, unique = true)
    private String phone;

    private String fullName;

    private Instant createdAt = Instant.now();

    public CustomerRecord(String customerId, String phone, String fullName) {
        this.customerId = customerId;
        this.phone = phone;
        this.fullName = fullName;
    }
}
