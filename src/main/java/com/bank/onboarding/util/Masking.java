package com.bank.onboarding.util;

public final class Masking {
    private Masking() {}

    /** Che SĐT khi ghi log — chỉ giữ 3 số đầu + 2 số cuối. VD: 0901111111 -> 090*****11 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 5) {
            return "***";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 5) + phone.substring(phone.length() - 2);
    }
}