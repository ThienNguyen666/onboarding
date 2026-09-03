package com.bank.onboarding.util;

import com.netflix.conductor.common.run.Workflow;

/**
 * Từ khi collect_phone_number chuyển sang human task (asyncComplete=true), SĐT không
 * còn nằm ở workflow.getInput() (input lúc start) nữa mà nằm trong output của task
 * collect_phone_number_ref. Đọc chung ở đây để tránh lặp lại bug đọc nhầm workflow.input.
 */
public final class WorkflowInputs {

    private WorkflowInputs() {}

    public static String phoneOf(Workflow workflow) {
        if (workflow.getTasks() == null) return null;
        Object phone = workflow.getTasks().stream()
                .filter(t -> "collect_phone_number_ref".equals(t.getReferenceTaskName()))
                .reduce((first, second) -> second)
                .map(t -> t.getOutputData())
                .map(out -> out.get("phone"))
                .orElse(null);
        return phone == null ? null : String.valueOf(phone);
    }
}