package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker; // <-- Import class này của SDK
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FIX: Tự động gom tất cả các class implements Worker đã được Spring quản lý 
 * (thay vì dùng hardcode 1 class OnboardingConductorWorkers gây lỗi DI hoặc NPE).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "conductor.worker", name = "auto-start", havingValue = "true")
public class ConductorTaskRunner implements SmartLifecycle {

    private final TaskClient taskClient;
    // Spring sẽ tự động tìm tất cả các Bean implements Worker và gộp thành List này
    private final List<Worker> workers; 
    private final ConductorProperties properties;
    
    private TaskRunnerConfigurer configurer;
    private volatile boolean running = false;

    public ConductorTaskRunner(TaskClient taskClient, List<Worker> workers,
                               ConductorProperties properties) {
        this.taskClient = taskClient;
        this.workers = workers;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (workers == null || workers.isEmpty()) {
            log.warn("Không tìm thấy Worker nào được đăng ký! Hãy kiểm tra lại các class Worker (nhớ gắn @Component và implements Worker).");
            return;
        }

        // Truyền thẳng list workers vào Builder, không cần List.of() nữa
        configurer = new TaskRunnerConfigurer.Builder(taskClient, workers)
                .withThreadCount(properties.worker().threadCount())
                .build();
                
        configurer.init();
        running = true;
        log.info("Conductor worker polling STARTED — threadCount={}, totalWorkersRegistered={}", 
                properties.worker().threadCount(), workers.size());
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
    public boolean isRunning() { 
        return running; 
    }

    @Override
    public int getPhase() { 
        return Integer.MAX_VALUE; 
    }
}