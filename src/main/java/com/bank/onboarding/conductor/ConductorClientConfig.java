package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import io.orkes.conductor.client.ApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class ConductorClientConfig {

      @Bean
      public ApiClient conductorApiClient(ConductorProperties properties) {
            var builder = ApiClient.builder().basePath(properties.server().url());
            if (StringUtils.hasText(properties.server().authKey()) && StringUtils.hasText(properties.server().authSecret())) {
                  builder.credentials(properties.server().authKey(), properties.server().authSecret());
            } else {
                  log.warn("CONDUCTOR_AUTH_KEY/SECRET chưa cấu hình — chỉ dùng được nếu server không yêu cầu auth");
            }
            ApiClient client = builder.build();
            log.info("Đã cấu hình Orkes Conductor client -> {}", properties.server().url());
            return client;
      }

      @Bean
      public TaskClient taskClient(ApiClient apiClient) {
            return new TaskClient(apiClient);
      }

      @Bean
      public WorkflowClient workflowClient(ApiClient apiClient) {
            return new WorkflowClient(apiClient);
      }
}