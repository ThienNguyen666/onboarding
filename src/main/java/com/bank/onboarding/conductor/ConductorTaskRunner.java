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
        try {
            List<Worker> workers = SpringWorkerAdapter.wrap(onboardingConductorWorkers, properties.worker().pollingIntervalMs());
            int effectiveThreadCount = Math.max(properties.worker().threadCount(), workers.size());
            configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
                    .withThreadCount(effectiveThreadCount)
                    .build();
            configurer.init();
            running = true;
            log.info("Conductor worker polling STARTED — {} worker(s), threadCount={}", workers.size(), effectiveThreadCount);
        } catch (Exception e) {
            log.error("Không khởi động được Conductor worker polling — kiểm tra CONDUCTOR_SERVER_URL/AUTH_KEY/AUTH_SECRET. " +
                    "App vẫn chạy tiếp nhưng workflow sẽ không được worker xử lý cho tới khi kết nối lại", e);
            running = false;
        }
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