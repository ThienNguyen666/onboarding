package com.bank.onboarding.controller;

import com.bank.onboarding.dto.TaskSignalRequest;
import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.bank.onboarding.service.ConductorTaskSignalService;
import com.bank.onboarding.service.ConductorWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Kích hoạt luồng chạy THẬT trên Orkes Cloud (khác với /api/onboarding/sessions demo tương tác). */
@RestController
@RequestMapping("/api/conductor")
@RequiredArgsConstructor
public class ConductorController {

      private final ConductorWorkflowService workflowService;
      private final ConductorTaskSignalService taskSignalService; 
      
      @PostMapping("/start")
      public Map<String, String> start(@RequestBody Map<String, Object> input) {
            return Map.of("workflowId", workflowService.start(input));
      }

      @GetMapping("/{workflowId}")
      public WorkflowStatusResponse status(@PathVariable String workflowId) {
            return workflowService.status(workflowId);
      }

      @PostMapping("/{workflowId}/tasks/{taskRef}/complete")
      public Map<String, Object> completeTask(@PathVariable String workflowId,
                                          @PathVariable String taskRef,
                                          @RequestBody TaskSignalRequest req) {
            return taskSignalService.completeTask(workflowId, taskRef, req);
      }
}