package com.bank.onboarding.dto;

public class AccountCreationDtos {

    /**
     * `forceComplianceResult` chỉ để demo/QA ép kết quả compliance (SUCCESS /
     * NEED_REVIEW / FAILED) thay vì để rule mock tự quyết theo SĐT.
     */
    public record CreateAccountRequest(
            String forceComplianceResult
    ) {}

    public record CreateAccountResponse(
            String ebankUserId,
            String accountNumber,
            String complianceStatus,   // SUCCESS | NEED_REVIEW | FAILED
            String linkId,             // chỉ có khi SUCCESS
            String failureReason,      // chỉ có khi FAILED
            String phase,
            String status
    ) {}

    private AccountCreationDtos() {}
}
