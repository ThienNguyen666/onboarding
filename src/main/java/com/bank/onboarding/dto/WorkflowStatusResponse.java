package com.bank.onboarding.dto;

import java.util.Map;

/**
 * Response gọn cho FE thay cho SessionStatusResponse cũ — map trực tiếp từ
 * Workflow (Orkes SDK). pendingTaskId chỉ có giá trị khi đang chờ KH thao tác
 * (task asyncComplete=true đang IN_PROGRESS) — FE dùng để gọi complete-task.
 */
public record WorkflowStatusResponse(
        String workflowId,
        String status,              // RUNNING | COMPLETED | FAILED | TERMINATED | PAUSED
        String currentTaskRef,
        String pendingTaskId,
        boolean awaitingCustomerInput,
        Map<String, Object> output,
        String reasonForIncompletion
) {}