package com.bank.onboarding.config;

import com.bank.onboarding.entity.CustomerRecord;
import com.bank.onboarding.repository.CustomerRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seed vài SĐT ETB có sẵn để demo nhánh "KH đã có tài khoản" mà không cần
 * ghi tay vào DB. Chạy idempotent (bỏ qua nếu đã có dữ liệu).
 */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final CustomerRecordRepository customerRecordRepository;

    @Override
    public void run(String... args) {
        if (customerRecordRepository.count() > 0) {
            return;
        }
        customerRecordRepository.save(new CustomerRecord("ETB-0001", "0901111111", "Tran Thi ETB Demo"));
        customerRecordRepository.save(new CustomerRecord("ETB-0002", "0902222222", "Le Van ETB Demo"));
    }
}
