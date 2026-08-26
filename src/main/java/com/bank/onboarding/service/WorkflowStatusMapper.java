package com.bank.onboarding.service;

import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Convert Workflow (Orkes) -> WorkflowStatusResponse. "Human task" = các
 * taskReferenceName có asyncComplete=true trong workflow JSON — FE phải gọi
 * complete-task API khi KH thao tác xong.
 */
@Component
public class WorkflowStatusMapper {

    private static final Set<String> HUMAN_TASK_REFS = Set.of(
            "loop_perform_ocr_ref", "loop_perform_liveness_ref",
            "loop_perform_nfc_ref", "verify_otp_ref");

    public WorkflowStatusResponse toResponse(Workflow workflow) {
        Task pending = findLatest(workflow.getTasks(),
                t -> HUMAN_TASK_REFS.contains(t.getReferenceTaskName()) && t.getStatus() == Task.Status.IN_PROGRESS);
        Task current = pending != null ? pending : findLatest(workflow.getTasks(),
                t -> t.getStatus() == Task.Status.IN_PROGRESS || t.getStatus() == Task.Status.SCHEDULED);

        return new WorkflowStatusResponse(
                workflow.getWorkflowId(),
                workflow.getStatus().name(),
                current != null ? current.getReferenceTaskName() : null,
                pending != null ? pending.getTaskId() : null,
                pending != null,
                workflow.getOutput(),
                workflow.getReasonForIncompletion());
    }

    private Task findLatest(List<Task> tasks, java.util.function.Predicate<Task> filter) {
        if (tasks == null) return null;
        return tasks.stream().filter(filter).reduce((first, second) -> second).orElse(null);
    }
}