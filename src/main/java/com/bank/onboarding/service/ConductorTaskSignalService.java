package com.bank.onboarding.service;

import com.bank.onboarding.dto.TaskSignalRequest;
import com.bank.onboarding.exception.OnboardingException;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Cầu nối cho 6 task asyncComplete=true trong Orkes — 
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

    public Map<String, Object> completeTask(String workflowId, String taskRefName, TaskSignalRequest req) {
        Workflow workflow = workflowClient.getWorkflow(workflowId, true);
        Task task = findInProgressTask(workflow, taskRefName);

        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId(workflow.getWorkflowId());
        result.setTaskId(task.getTaskId());
        result.setStatus(TaskResult.Status.COMPLETED); // pass/fail thật do task validate_*/SWITCH phía sau quyết định

        switch (taskRefName) {
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
            case "verify_otp_ref" -> result.setOutputData(verifyOtp(workflow, req));
            default -> throw OnboardingException.badRequest(
                    "Task '" + taskRefName + "' không hỗ trợ complete thủ công");
        }

        taskClient.updateTask(result);
        log.info("Completed async task ref={} workflowId={}", taskRefName, workflowId);
        return result.getOutputData();
    }

    private Map<String, Object> verifyOtp(Workflow workflow, TaskSignalRequest req) {
        String phone = String.valueOf(workflow.getInput().get("phone"));
        String otpKey = "conductor:" + phone;
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

    private Task findInProgressTask(Workflow workflow, String taskRefName) {
        return workflow.getTasks().stream()
                .filter(t -> taskRefName.equals(t.getReferenceTaskName()) && t.getStatus() == Task.Status.IN_PROGRESS)
                .reduce((first, second) -> second)
                .orElseThrow(() -> OnboardingException.badState(
                        "Task '" + taskRefName + "' hiện không ở trạng thái IN_PROGRESS trong workflow " + workflow.getWorkflowId()));
    }
}