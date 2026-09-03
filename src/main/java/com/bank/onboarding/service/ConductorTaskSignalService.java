package com.bank.onboarding.service;

import com.bank.onboarding.dto.TaskSignalRequest;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.repository.OnboardingSessionRepository;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cầu nối cho các task asyncComplete=true trong Orkes — 
 * worker chỉ "pickup", kết quả thật do FE gửi lên
 * đây rồi backend gọi TaskClient.updateTask() để Conductor engine đi tiếp.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConductorTaskSignalService {

    private final WorkflowClient workflowClient;
    private final TaskClient taskClient;
    private final MockEkycService mockEkycService;
    private final OtpService otpService;
    private final CustomerDirectoryService customerDirectoryService;
    private final OnboardingSessionRepository sessionRepository;
    private final OnboardingProperties onboardingProperties;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");
    
    private static final Set<Task.Status> COMPLETABLE_STATUSES =
            Set.of(Task.Status.IN_PROGRESS, Task.Status.SCHEDULED);
    

    public Map<String, Object> completeTask(String workflowId, String taskRefName, TaskSignalRequest rawReq) {
        TaskSignalRequest req = guardForceFail(rawReq);
        Task task = findInProgressTaskWithRetry(workflowId, taskRefName);

        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId(workflowId);
        result.setTaskId(task.getTaskId());
        result.setStatus(TaskResult.Status.COMPLETED); // pass/fail thật do task validate_*/SWITCH phía sau quyết định

        switch (taskRefName) {
            case "show_cvp_ref" -> result.setOutputData(Map.of("consented", true));
            case "collect_phone_number_ref" -> result.setOutputData(collectPhone(workflowId, req));
            case "show_identity_confirmation_ref" -> result.setOutputData(Map.of("confirmed", true));
            case "show_tnc_screen_ref" -> result.setOutputData(Map.of("tncAccepted", true));
            case "loop_perform_ocr_ref" ->
                    result.setOutputData(Map.of(
                            "ocrData", mockEkycService.mockCccdData(req.outputData()),
                            "forceFail", req.forceFail()));
            case "loop_perform_liveness_ref" ->
                    result.setOutputData(Map.of(
                            "livenessData", mockEkycService.mockLivenessData(req.outputData()),
                            "forceFail", req.forceFail()));
            case "loop_perform_nfc_ref" ->
                    result.setOutputData(Map.of(
                            "nfcData", mockEkycService.mockNfcData(req.outputData()),
                            "forceFail", req.forceFail()));
            case "verify_otp_ref" -> result.setOutputData(verifyOtp(workflowClient.getWorkflow(workflowId, true), req));
                default -> throw OnboardingException.badRequest(
                    "Task '" + taskRefName + "' không hỗ trợ complete thủ công");
        }

        taskClient.updateTask(result);
        log.info("Completed async task ref={} workflowId={}", taskRefName, workflowId);
        return result.getOutputData();
    }

    
    private Map<String, Object> verifyOtp(Workflow workflow, TaskSignalRequest req) {
        String phone = String.valueOf(workflow.getInput().get("phone"));
        String otpKey = OtpService.workflowSessionKey(phone);
        Object otpValue = req.outputData() == null ? null : req.outputData().get("otp");
        boolean verified;
        try {
            verified = !req.forceFail() && otpValue != null && otpService.verify(otpKey, String.valueOf(otpValue));
        } catch (OnboardingException ex) {
            log.warn("OTP verify lỗi workflowId={}: {}", workflow.getWorkflowId(), ex.getMessage());
            verified = false;
        }
        if (verified) {
            customerDirectoryService.clearDropoff(phone);
        }
        return Map.of("verified", verified);
    }
    public Map<String, Object> resendOtp(String workflowId) {
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task pending = findInProgressTask(workflow, "verify_otp_ref");
        if (pending == null) {
            throw OnboardingException.badState("Workflow hiện không ở bước chờ nhập OTP, không thể gửi lại");
        }
        String phone = String.valueOf(workflow.getInput().get("phone"));
        otpService.generateAndStore(OtpService.workflowSessionKey(phone));
        log.info("OTP resent workflowId={}", workflowId);
        return Map.of("resent", true);
    }

    private Map<String, Object> collectPhone(String workflowId, TaskSignalRequest req) {
        Object raw = req.outputData() == null ? null : req.outputData().get("phone");
        String phone = raw == null ? null : String.valueOf(raw).trim();
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw OnboardingException.badRequest("phone phải là SĐT VN 10 số, bắt đầu bằng 0");
        }
        sessionRepository.findByWorkflowId(workflowId).ifPresent(s -> {
            s.setPhone(phone);
            sessionRepository.save(s);
        });
        return Map.of("phone", phone);
    }

    private Task findInProgressTaskWithRetry(String workflowId, String taskRefName) {
        // Budget đủ lớn: chịu được worker pollingIntervalMs (mặc định 1000ms) + độ trễ Orkes Cloud
        // + trường hợp hàng đợi task cùng loại (perform_ocr_cccd/liveness/nfc/...) đang tồn đọng.
        int maxAttempts = 20;
        long delayMs = 400;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Workflow workflow = workflowClient.getWorkflow(workflowId, true);
                Task task = findInProgressTask(workflow, taskRefName);
                if (task != null) {
                    return task;
                }
            } catch (Exception e) {
                log.warn("getWorkflow lỗi tạm thời (attempt {}/{}): {}", attempt + 1, maxAttempts, e.getMessage());
            }
            try {
                Thread.sleep(Math.min(delayMs, 3000));
                delayMs = (long) (delayMs * 1.6);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    throw OnboardingException.badState(
        "Task '" + taskRefName + "' hiện không ở trạng thái SCHEDULED/IN_PROGRESS trong workflow " + workflowId
                + " (khả năng: worker BE chưa poll kịp, hoặc mất kết nối tới Orkes Cloud)");
    }   

    private Task findInProgressTask(Workflow workflow, String taskRefName) {
        return workflow.getTasks().stream()
                .filter(t -> matchesRef(t.getReferenceTaskName(), taskRefName)
                        && COMPLETABLE_STATUSES.contains(t.getStatus()))
                .reduce((first, second) -> second)
                .orElse(null);
    }
    // Cùng gốc bug với WorkflowStatusMapper: task trong DO_WHILE có referenceTaskName runtime
    // dạng "<ref>__<iteration>". So khớp cả 2 dạng (có/không hậu tố).
    private TaskSignalRequest guardForceFail(TaskSignalRequest req) {
        if (req.forceFail() && !onboardingProperties.otp().debugEndpointEnabled()) {
            log.warn("forceFail bị bỏ qua vì debug endpoint đang tắt — task sẽ chạy nhánh PASS bình thường");
            return new TaskSignalRequest(false, req.outputData());
        }
        return req;
    }
    private boolean matchesRef(String actualRef, String expectedRef) {
        return actualRef.equals(expectedRef) || actualRef.startsWith(expectedRef + "__");
    }
}