package com.bank.onboarding.service;

import com.bank.onboarding.config.ConductorProperties;
import com.bank.onboarding.config.OnboardingProperties;
import com.bank.onboarding.dto.WorkflowStatusResponse;
import com.bank.onboarding.entity.OnboardingSession;
import com.bank.onboarding.exception.OnboardingException;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.bank.onboarding.dto.StartOnboardingRequest;

import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
      
      @CircuitBreaker(name = "orkes", fallbackMethod = "startFallback")
      @Retry(name = "orkes")
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
      
      private String startFallback(StartOnboardingRequest req, Throwable t) {
            log.error("Orkes gián đoạn khi start workflow vendorId={}", req.vendorId(), t);
            throw OnboardingException.badState("Hệ thống eKYC đang gián đoạn tạm thời, vui lòng thử lại sau ít phút");
      }    
      
      @CircuitBreaker(name = "orkes", fallbackMethod = "dropoffFallback")
      @Retry(name = "orkes")  
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

      private void dropoffFallback(String workflowId, Throwable t) {
            log.error("Orkes gián đoạn khi mark dropoff workflowId={}", workflowId, t);
            // không throw — dropoff chỉ là optimization UX, fail âm thầm là chấp nhận được
      }
      @CircuitBreaker(name = "orkes", fallbackMethod = "statusFallback")
      @Retry(name = "orkes")
      public WorkflowStatusResponse status(String workflowId) {
            Workflow workflow = workflowClient.getWorkflow(workflowId, true);
            return statusMapper.toResponse(workflow);
      }
      private WorkflowStatusResponse statusFallback(String workflowId, Throwable t) {
            log.error("Orkes gián đoạn khi lấy status workflowId={}", workflowId, t);
            throw OnboardingException.badState("Không lấy được trạng thái phiên, vui lòng thử lại sau ít phút");
      }
}