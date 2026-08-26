package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FIX: trước đây dùng WorkflowExecutor.initWorkers(packageName) -> SDK tự
 * `new OnboardingConductorWorkers()` bằng reflection -> NPE vì class dùng
 * @RequiredArgsConstructor (không có no-arg constructor). Nay dùng
 * TaskRunnerConfigurer, truyền thẳng bean Spring đã inject đầy đủ dependency.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "conductor.worker", name = "auto-start", havingValue = "true")
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
            configurer = new TaskRunnerConfigurer.Builder(taskClient, List.of(workers))
                  .withThreadCount(properties.worker().threadCount())
                  .build();
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
      public int getPhase() { return Integer.MAX_VALUE; }
}