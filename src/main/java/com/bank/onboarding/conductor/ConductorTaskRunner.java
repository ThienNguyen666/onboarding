package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import io.orkes.conductor.client.OrkesClients;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Khởi động worker polling lên Orkes Cloud khi app start, dừng gọn khi app
 * shutdown (server.shutdown=graceful đã cấu hình ở application.yaml).
 */
@Slf4j
@Component
public class ConductorTaskRunner implements SmartLifecycle {

      private final TaskClient taskClient;
      private final OnboardingConductorWorkers workers;
      private final ConductorProperties properties;
      private TaskRunnerConfigurer configurer;
      private volatile boolean running = false;

      public ConductorTaskRunner(TaskClient taskClient, OnboardingConductorWorkers workers,
                                    ConductorProperties properties) {
            this.taskClient = taskClient;
            this.workers = workers;
            this.properties = properties;
      }

      @Override
      public void start() {
            configurer = new TaskRunnerConfigurer.Builder(taskClient, java.util.List.of())
                  .withThreadCount(properties.worker().threadCount())
                  .build();
            // Auto-discover @WorkerTask trên bean workers thay vì implement Worker interface thủ công
            var executor = new com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor(
                  taskClient.getApiClient() instanceof io.orkes.conductor.client.ApiClient apiClient
                              ? apiClient : null,
                  properties.worker().threadCount());
            executor.initWorkers(workers.getClass().getPackageName());
            configurer.init();
            running = true;
            log.info("Conductor worker polling STARTED — threadCount={}", properties.worker().threadCount());
      }

      @Override
      @PreDestroy
      public void stop() {
            if (configurer != null) {
                  configurer.shutdown();
            }
            running = false;
            log.info("Conductor worker polling STOPPED");
      }

      @Override
      public boolean isRunning() { return running; }

      @Override
      public int getPhase() { return Integer.MAX_VALUE; } // start sau cùng, stop đầu tiên
}