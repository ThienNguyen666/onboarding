package com.bank.onboarding.service;

import com.bank.onboarding.config.ConductorProperties;
import com.bank.onboarding.config.OnboardingProperties;
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
      private final CustomerDirectoryService customerDirectoryService;
      private final OnboardingProperties onboardingProperties;
      
      public String start(Map<String, Object> input) {
            Map<String, Object> effectiveInput = new java.util.HashMap<>(input);
            effectiveInput.putIfAbsent("maxOcrRetries", onboardingProperties.retry().defaultMaxOcrRetries());
            effectiveInput.putIfAbsent("maxLivenessRetries", onboardingProperties.retry().defaultMaxLivenessRetries());
            effectiveInput.putIfAbsent("maxNfcRetries", onboardingProperties.retry().defaultMaxNfcRetries());

            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(properties.workflow().name());
            request.setVersion(properties.workflow().version());
            request.setInput(effectiveInput);
            String workflowId = workflowClient.startWorkflow(request);

            String vendorId = String.valueOf(effectiveInput.getOrDefault("vendorId", "UNKNOWN"));
            String phone = effectiveInput.get("phone") == null ? null : String.valueOf(effectiveInput.get("phone"));
            sessionRepository.save(new OnboardingSession(workflowId, vendorId, phone));
            log.info("Started workflow {} vendorId={}", workflowId, vendorId);
            return workflowId;
      }    
      public void dropoff(String workflowId) {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            Object phoneRaw = workflow.getInput() == null ? null : workflow.getInput().get("phone");
            if (phoneRaw == null) {
                  return; // chưa nhập SĐT thì không có gì để resume
            }
            String resumeStep = statusMapper.toResponse(workflow).currentTaskRef();
            customerDirectoryService.markDropoff(String.valueOf(phoneRaw), workflowId,
                  resumeStep == null ? "UNKNOWN" : resumeStep);
      }

      public WorkflowStatusResponse status(String workflowId) {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            return statusMapper.toResponse(workflow);
      }
}