package com.bank.onboarding.service;

import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import com.bank.onboarding.domain.HumanTaskRefs;

/**
 * Convert Workflow (Orkes) -> WorkflowStatusResponse. "Human task" = các
 * taskReferenceName có asyncComplete=true trong workflow JSON — FE phải gọi
 * complete-task API khi KH thao tác xong.
 */
@Component
public class WorkflowStatusMapper {

        public WorkflowStatusResponse toResponse(Workflow workflow) {
        Task pending = findLatest(workflow.getTasks(),
                t -> HumanTaskRefs.REFS.contains(baseRef(t.getReferenceTaskName()))
                        && (t.getStatus() == Task.Status.IN_PROGRESS || t.getStatus() == Task.Status.SCHEDULED));
        Task current = pending != null ? pending : findLatest(workflow.getTasks(),
                t -> t.getStatus() == Task.Status.IN_PROGRESS || t.getStatus() == Task.Status.SCHEDULED);

        Integer iteration = null, maxRetries = null;
        String loopRef = pending != null ? HumanTaskRefs.TO_LOOP_REF.get(baseRef(pending.getReferenceTaskName())) : null;        
        if (loopRef != null) {
                Task loop = findLatest(workflow.getTasks(), t -> loopRef.equals(t.getReferenceTaskName()));
                if (loop != null && loop.getOutputData() != null) {
                        Object it = loop.getOutputData().get("iteration");
                        if (it instanceof Number n) iteration = n.intValue();
                }
                // FIX: trước đây luôn lấy maxOcrRetries bất kể đang ở loop nào -> retryMax hiện sai
                // khi maxLivenessRetries/maxNfcRetries khác maxOcrRetries.
                Object max = workflow.getInput() == null ? null : workflow.getInput().get(LOOP_REF_TO_MAX_KEY.get(loopRef));
                if (max instanceof Number n) maxRetries = n.intValue();
        }

        return new WorkflowStatusResponse(
                workflow.getWorkflowId(),
                workflow.getStatus().name(),
                current != null ? baseRef(current.getReferenceTaskName()) : null, // trả về ref GỐC cho FE
                pending != null ? pending.getTaskId() : null,
                pending != null,
                workflow.getOutput(),
                workflow.getReasonForIncompletion(),
                iteration,
                maxRetries);
        }

        private static final Map<String, String> LOOP_REF_TO_MAX_KEY = Map.of(
                "ocr_cccd_retry_loop_ref", "maxOcrRetries",
                "liveness_retry_loop_ref", "maxLivenessRetries",
                "nfc_retry_loop_ref", "maxNfcRetries");

        /** Conductor tự thêm "__<iteration>" vào referenceTaskName của task bên trong DO_WHILE lúc
         * runtime (vd loop_perform_ocr_ref -> loop_perform_ocr_ref__2). Bóc hậu tố này ra để so khớp
         * đúng với ref gốc khai báo trong workflow JSON, đồng thời trả về ref GỐC cho FE (FE match
         * exact string "loop_perform_ocr_ref", không biết gì về hậu tố iteration). */
        private String baseRef(String actualRef) {
                int idx = actualRef.indexOf("__");
                return idx == -1 ? actualRef : actualRef.substring(0, idx);
        }
        private Task findLatest(List<Task> tasks, java.util.function.Predicate<Task> filter) {
                if (tasks == null) return null;
                return tasks.stream().filter(filter).reduce((first, second) -> second).orElse(null);
        }
}