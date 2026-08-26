package com.bank.onboarding.conductor;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.ApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "conductor.worker", name = "auto-start", havingValue = "true")
public class ConductorTaskRunner implements SmartLifecycle {

    private static final String WORKER_PACKAGE = "com.bank.onboarding.conductor";

    private final ApiClient apiClient;
    private final ConductorProperties properties;

    private volatile boolean running = false;

    @Override
    public void start() {
        WorkflowExecutor executor = new WorkflowExecutor(apiClient, properties.worker().threadCount());
        executor.initWorkers(WORKER_PACKAGE);
        running = true;
        log.info("Conductor worker polling STARTED — quét package={}, threadCount={}",
                WORKER_PACKAGE, properties.worker().threadCount());
    }

    @Override
    public void stop() {
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