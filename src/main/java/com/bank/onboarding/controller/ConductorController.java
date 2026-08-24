package com.bank.onboarding.controller;

import com.bank.onboarding.service.ConductorWorkflowService;
import com.netflix.conductor.common.run.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Kích hoạt luồng chạy THẬT trên Orkes Cloud (khác với /api/onboarding/sessions demo tương tác). */
@RestController
@RequestMapping("/api/conductor")
@RequiredArgsConstructor
public class ConductorController {

      private final ConductorWorkflowService workflowService;

      @PostMapping("/start")
      public Map<String, String> start(@RequestBody Map<String, Object> input) {
            return Map.of("workflowId", workflowService.start(input));
      }

      @GetMapping("/{workflowId}")
      public Workflow status(@PathVariable String workflowId) {
            return workflowService.status(workflowId);
      }
}