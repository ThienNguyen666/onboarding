package com.bank.onboarding.service;

import com.bank.onboarding.config.ConductorProperties;
import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.bank.onboarding.entity.OnboardingSession;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConductorWorkflowService {

      private final WorkflowClient workflowClient;
      private final ConductorProperties properties;
      private final OnboardingSessionRepository sessionRepository;
      private final WorkflowStatusMapper statusMapper;

      public String start(Map<String, Object> input) {
            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(properties.workflow().name());
            request.setVersion(properties.workflow().version());
            request.setInput(input);
            String workflowId = workflowClient.startWorkflow(request);

            String vendorId = String.valueOf(input.getOrDefault("vendorId", "UNKNOWN"));
            String phone = input.get("phone") == null ? null : String.valueOf(input.get("phone"));
            sessionRepository.save(new OnboardingSession(workflowId, vendorId, phone));
            log.info("Started workflow {} vendorId={}", workflowId, vendorId);
            return workflowId;
      }
      public WorkflowStatusResponse status(String workflowId) {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            return statusMapper.toResponse(workflow);
      }
}