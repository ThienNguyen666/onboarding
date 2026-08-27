package com.bank.onboarding.service;

import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.Map;
/**
 * Convert Workflow (Orkes) -> WorkflowStatusResponse. "Human task" = các
 * taskReferenceName có asyncComplete=true trong workflow JSON — FE phải gọi
 * complete-task API khi KH thao tác xong.
 */
@Component
public class WorkflowStatusMapper {

        private static final Set<String> HUMAN_TASK_REFS = Set.of(
                "loop_perform_ocr_ref", "loop_perform_liveness_ref",
                "loop_perform_nfc_ref", "verify_otp_ref",
                "show_identity_confirmation_ref", "show_tnc_screen_ref");
                
        private static final Map<String, String> HUMAN_TASK_TO_LOOP_REF = Map.of(
                "loop_perform_ocr_ref", "ocr_cccd_retry_loop_ref",
                "loop_perform_liveness_ref", "liveness_retry_loop_ref",
                "loop_perform_nfc_ref", "nfc_retry_loop_ref");

        public WorkflowStatusResponse toResponse(Workflow workflow) {
                Task pending = findLatest(workflow.getTasks(),
                        t -> HUMAN_TASK_REFS.contains(t.getReferenceTaskName()) && t.getStatus() == Task.Status.IN_PROGRESS);
                Task current = pending != null ? pending : findLatest(workflow.getTasks(),
                        t -> t.getStatus() == Task.Status.IN_PROGRESS || t.getStatus() == Task.Status.SCHEDULED);

                Integer iteration = null, maxRetries = null;
                String loopRef = pending != null ? HUMAN_TASK_TO_LOOP_REF.get(pending.getReferenceTaskName()) : null;
                if (loopRef != null) {
                Task loop = findLatest(workflow.getTasks(), t -> loopRef.equals(t.getReferenceTaskName()));
                if (loop != null && loop.getOutputData() != null) {
                        Object it = loop.getOutputData().get("iteration");
                        if (it instanceof Number n) iteration = n.intValue();
                }
                Object maxOcr = workflow.getInput() == null ? null :
                        workflow.getInput().getOrDefault("maxOcrRetries",
                        workflow.getInput().getOrDefault("maxLivenessRetries", workflow.getInput().get("maxNfcRetries")));
                if (maxOcr instanceof Number n) maxRetries = n.intValue();
                }

                return new WorkflowStatusResponse(
                        workflow.getWorkflowId(),
                        workflow.getStatus().name(),
                        current != null ? current.getReferenceTaskName() : null,
                        pending != null ? pending.getTaskId() : null,
                        pending != null,
                        workflow.getOutput(),
                        workflow.getReasonForIncompletion(),
                        iteration,
                        maxRetries);
        }

        private Task findLatest(List<Task> tasks, java.util.function.Predicate<Task> filter) {
                if (tasks == null) return null;
                return tasks.stream().filter(filter).reduce((first, second) -> second).orElse(null);
        }
}