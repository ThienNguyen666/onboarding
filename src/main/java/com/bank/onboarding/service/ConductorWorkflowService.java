package com.bank.onboarding.service;

import com.bank.onboarding.config.ConductorProperties;
import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.bank.onboarding.entity.OnboardingSession;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.bank.onboarding.dto.StartOnboardingRequest;

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
      
      public String start(StartOnboardingRequest req) {
            String forceCompliance = req.forceComplianceResult();
            if (forceCompliance != null && !onboardingProperties.otp().debugEndpointEnabled()) {
                  log.warn("forceComplianceResult bị bỏ qua vì debug endpoint đang tắt (vendorId={})", req.vendorId());
                  forceCompliance = null;
            }

            Map<String, Object> input = new java.util.HashMap<>();
            input.put("vendorClientId", req.vendorClientId());
            input.put("vendorClientSecret", req.vendorClientSecret());
            input.put("sdkSessionId", req.sdkSessionId());
            input.put("productType", req.productType());
            input.put("deviceInfo", Map.of(
                  "model", req.deviceInfo().model(),
                  "osVersion", req.deviceInfo().osVersion(),
                  "nfcSupported", req.deviceInfo().nfcSupported()));
            input.put("phone", req.phone());
            input.put("vendorId", req.vendorId());
            input.put("maxOcrRetries", onboardingProperties.retry().defaultMaxOcrRetries());
            input.put("maxLivenessRetries", onboardingProperties.retry().defaultMaxLivenessRetries());
            input.put("maxNfcRetries", onboardingProperties.retry().defaultMaxNfcRetries());
            if (forceCompliance != null) input.put("forceComplianceResult", forceCompliance);

            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(properties.workflow().name());
            request.setVersion(properties.workflow().version());
            request.setInput(input);
            String workflowId = workflowClient.startWorkflow(request);

            sessionRepository.save(new OnboardingSession(workflowId, req.vendorId(), req.phone()));
            log.info("Started workflow {} vendorId={}", workflowId, req.vendorId());
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