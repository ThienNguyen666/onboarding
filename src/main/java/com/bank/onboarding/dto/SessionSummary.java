package com.bank.onboarding.dto;

import java.time.Instant;

public record SessionSummary(
      String workflowId, String phoneMasked,
      String lastKnownStatus, Instant createdAt
) {}