package com.bank.onboarding.dto;

import java.time.Instant;

public record SessionSummary(
      String sessionId, String phoneMasked, 
      String phase, String status, 
      Instant createdAt
) {}