package com.bank.onboarding.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "conductor")
public record ConductorProperties(Server server, Worker worker, Workflow workflow) {

      public record Server(
            @NotBlank String url,
            String authKey,
            String authSecret
      ) {}

      public record Worker(
            @Min(1) @DefaultValue("10") int threadCount,
            @Min(50) @DefaultValue("100") int pollingIntervalMs
      ) {}

      public record Workflow(
            @NotBlank String name,
            @Min(1) @DefaultValue("2") int version
      ) {}
}