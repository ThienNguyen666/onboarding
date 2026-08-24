package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.OrkesClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kết nối tới Orkes Conductor Developer Edition (cloud instance).
 * ApiClient.builder() tự đọc CONDUCTOR_SERVER_URL / CONDUCTOR_AUTH_KEY /
 * CONDUCTOR_AUTH_SECRET từ ENV nếu có — ở đây ta set tường minh từ
 * ConductorProperties (application.yaml) để không phụ thuộc thứ tự load ENV.
 */
@Slf4j
@Configuration
public class ConductorClientConfig {

      @Bean
      public ApiClient conductorApiClient(ConductorProperties properties) {
            var builder = ApiClient.builder().basePath(properties.server().url());
            if (hasText(properties.server().authKey()) && hasText(properties.server().authSecret())) {
                  builder.credentials(properties.server().authKey(), properties.server().authSecret());
            } else {
                  log.warn("CONDUCTOR_AUTH_KEY/SECRET chưa cấu hình — chỉ dùng được nếu server không yêu cầu auth");
            }
            ApiClient client = builder.build();
            log.info("Đã cấu hình Orkes Conductor client -> {}", properties.server().url());
            return client;
      }

      @Bean
      public OrkesClients orkesClients(ApiClient apiClient) {
            return new OrkesClients(apiClient);
      }

      @Bean
      public TaskClient taskClient(OrkesClients clients) {
            return clients.getTaskClient();
      }

      @Bean
      public WorkflowClient workflowClient(OrkesClients clients) {
            return clients.getWorkflowClient();
      }

      private boolean hasText(String s) {
            return s != null && !s.isBlank();
      }
}