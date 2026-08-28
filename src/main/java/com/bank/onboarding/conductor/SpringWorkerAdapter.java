package com.bank.onboarding.conductor;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bọc method @WorkerTask trên 1 bean Spring (đã inject đầy đủ dependency)
 * thành Worker chuẩn của Conductor SDK, đăng ký qua TaskRunnerConfigurer.
 *
 * KHÔNG dùng WorkflowExecutor.initWorkers(package) — cơ chế đó tự quét
 * classpath tìm @WorkerTask và SILENTLY FAILS trong Spring Boot fat jar
 * (BOOT-INF/classes khác cấu trúc jar thường). Ở đây reflect trực tiếp trên
 * 1 OBJECT đã tồn tại sẵn (bean Spring, classloader đã resolve xong) — không
 * quét jar — nên chạy ổn định 100% bất kể đóng gói kiểu gì.
 */
@Slf4j
public class SpringWorkerAdapter implements Worker {

    private static final Set<String> ASYNC_COMPLETE_TASKS = Set.of(
            "perform_ocr_cccd", "perform_liveness", "perform_nfc",
            "verify_otp", "show_identity_confirmation", "show_tnc_screen"
    );
    
    private final Object bean;
    private final Method method;
    private final String taskDefName;
    private final int pollingIntervalMs;
    private static final long ASYNC_COMPLETE_CALLBACK_SECONDS = 1700;
    
    private SpringWorkerAdapter(Object bean, Method method, int pollingIntervalMs) {
        this.bean = bean;
        this.method = method;
        this.method.setAccessible(true);
        this.taskDefName = method.getAnnotation(WorkerTask.class).value();
        this.pollingIntervalMs = pollingIntervalMs;
    }

    /** Quét toàn bộ method public có @WorkerTask trên 1 bean -> danh sách Worker. */
    public static List<Worker> wrap(Object bean, int pollingIntervalMs) {
        List<Worker> workers = new ArrayList<>();
        for (Method m : bean.getClass().getMethods()) {
            if (m.isAnnotationPresent(WorkerTask.class)) {
                workers.add(new SpringWorkerAdapter(bean, m, pollingIntervalMs));
            }
        }
        return workers;
    }

    @Override
    public String getTaskDefName() {
        return taskDefName;
    }
    
    @Override
    public int getPollingInterval() {
        return pollingIntervalMs;
    }
    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {
            Object output = method.invoke(bean, resolveArgs(task));
            boolean isAsyncComplete = ASYNC_COMPLETE_TASKS.contains(taskDefName);

            result.setStatus(isAsyncComplete ? TaskResult.Status.IN_PROGRESS : TaskResult.Status.COMPLETED);
            if (isAsyncComplete) {
                // Không set cái này = bug đang gặp: task visible lại ngay -> tự poll
                // lặp vô hạn, đè output thật của FE.
                result.setCallbackAfterSeconds(ASYNC_COMPLETE_CALLBACK_SECONDS);
            }

            if (output instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (v != null) result.getOutputData().put(String.valueOf(k), v);
                });
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Worker '{}' lỗi taskId={}", taskDefName, task.getTaskId(), cause);
            result.setStatus(TaskResult.Status.FAILED);
            result.setReasonForIncompletion(cause.getMessage());
        }
        return result;
    }   
    private Object[] resolveArgs(Task task) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        Map<String, Object> input = task.getInputData();
        for (int i = 0; i < params.length; i++) {
            InputParam ip = params[i].getAnnotation(InputParam.class);
            String key = ip != null ? ip.value() : params[i].getName();
            args[i] = input.get(key);
        }
        return args;
    }
}