package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.ApiClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Khởi động worker polling lên Orkes Cloud khi app start, dừng gọn khi app
 * shutdown. Chỉ chạy khi conductor.worker.auto-start=true (xem
 * ConductorProperties) — mặc định false để không cần Orkes Cloud credentials
 * ở demo local (OnboardingOrchestrationService).
 */
@Slf4j
@Component
public class ConductorTaskRunner implements SmartLifecycle {

      private final ApiClient apiClient;
      private final OnboardingConductorWorkers workers;
      private final ConductorProperties properties;
      private WorkflowExecutor executor;
      private volatile boolean running = false;

      public ConductorTaskRunner(ApiClient apiClient, OnboardingConductorWorkers workers,
                                    ConductorProperties properties) {
            this.apiClient = apiClient;
            this.workers = workers;
            this.properties = properties;
      }

      @Override
      public void start() {
            executor = new WorkflowExecutor(apiClient, properties.worker().threadCount());
            // Auto-discover @WorkerTask trong package của bean workers
            executor.initWorkers(workers.getClass().getPackageName());
            running = true;
            log.info("Conductor worker polling STARTED — threadCount={}", properties.worker().threadCount());
      }

      @Override
      @PreDestroy
      public void stop() {
            if (executor != null) {
                  executor.shutdown();
            }
            running = false;
            log.info("Conductor worker polling STOPPED");
      }

      @Override
      public boolean isRunning() { return running; }

      @Override
      public int getPhase() { return Integer.MAX_VALUE; } // start sau cùng, stop đầu tiên
}