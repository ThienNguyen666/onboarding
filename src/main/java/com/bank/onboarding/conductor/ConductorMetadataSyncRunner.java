package com.bank.onboarding.conductor;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "conductor.worker", name = "auto-start", havingValue = "true")
public class ConductorMetadataSyncRunner {

      private final MetadataClient metadataClient;

      private final ObjectMapper mapper = JsonMapper.builder()
                  .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .build();

      @EventListener(ApplicationReadyEvent.class)
      public void syncMetadata() throws Exception {
            List<TaskDef> taskDefs = mapper.readValue(
                  new ClassPathResource("conductor/task_definitions.json").getInputStream(),
                  mapper.getTypeFactory().constructCollectionType(List.class, TaskDef.class));
            try {
                  metadataClient.registerTaskDefs(taskDefs);
            } catch (Exception e) {
                  taskDefs.forEach(metadataClient::updateTaskDef);
            }
            log.info("Đồng bộ {} task definitions lên Orkes Cloud xong", taskDefs.size());

            WorkflowDef workflowDef = mapper.readValue(
                  new ClassPathResource("conductor/vendor_sdk_ekyc_account_opening.json").getInputStream(),
                  WorkflowDef.class);
            metadataClient.updateWorkflowDefs(List.of(workflowDef));
            log.info("Đồng bộ workflow '{}' v{} lên Orkes Cloud xong (asyncComplete flags được áp dụng)",
                  workflowDef.getName(), workflowDef.getVersion());
      }
}