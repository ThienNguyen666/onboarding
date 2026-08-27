package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Khởi động/tắt worker polling qua TaskRunnerConfigurer (List<Worker> tường
 * minh) — cách đăng ký ĐƯỢC ORKES KHUYẾN NGHỊ thay cho
 * WorkflowExecutor.initWorkers(package), vốn khiến toàn bộ SIMPLE task đứng
 * SCHEDULED vĩnh viễn trong fat jar (buộc complete tay trên Orkes Cloud UI).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "conductor.worker", name = "auto-start", havingValue = "true")
public class ConductorTaskRunner implements SmartLifecycle {

    private final TaskClient taskClient;
    private final ConductorProperties properties;
    private final OnboardingConductorWorkers onboardingConductorWorkers;

    private TaskRunnerConfigurer configurer;
    private volatile boolean running = false;

    @Override
    public void start() {
        List<Worker> workers = SpringWorkerAdapter.wrap(onboardingConductorWorkers);
        configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
                .withThreadCount(properties.worker().threadCount())
                .build();
        configurer.init();
        running = true;
        log.info("Conductor worker polling STARTED — {} worker(s) qua TaskRunnerConfigurer, threadCount={}",
                workers.size(), properties.worker().threadCount());
        workers.forEach(w -> log.info("  -> worker sẵn sàng cho task '{}'", w.getTaskDefName()));
    }

    @Override
    public void stop() {
        if (configurer != null) {
            configurer.shutdown();
        }
        running = false;
        log.info("Conductor worker polling STOPPED (app shutting down)");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}