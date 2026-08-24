package com.bank.onboarding.service;

import com.bank.onboarding.config.ConductorProperties;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConductorWorkflowService {

      private final WorkflowClient workflowClient;
      private final ConductorProperties properties;

      public String start(Map<String, Object> input) {
            StartWorkflowRequest request = new StartWorkflowRequest();
            request.setName(properties.workflow().name());
            request.setVersion(properties.workflow().version());
            request.setInput(input);
            return workflowClient.startWorkflow(request);
      }

      public Workflow status(String workflowId) {
            return workflowClient.getWorkflow(workflowId, true);
      }
}