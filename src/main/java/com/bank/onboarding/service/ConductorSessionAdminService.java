package com.bank.onboarding.service;

import com.bank.onboarding.dto.SessionSummary;
import com.bank.onboarding.entity.OnboardingSession;
import com.bank.onboarding.repository.AuditLogRepository;
import com.bank.onboarding.repository.OnboardingSessionRepository;
import com.bank.onboarding.util.Masking;
import com.netflix.conductor.client.http.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConductorSessionAdminService {

    private final OnboardingSessionRepository sessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final WorkflowClient workflowClient;

    @Transactional(readOnly = true)
    public List<SessionSummary> listRecentSessions() {
        return sessionRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(s -> new SessionSummary(s.getWorkflowId(), Masking.phone(s.getPhone()),
                        s.getLastKnownStatus() == null ? "IN_PROGRESS" : s.getLastKnownStatus(),
                        s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void resetAllData() {
        List<OnboardingSession> allSessions = sessionRepository.findAll();
        for (OnboardingSession session : allSessions) {
            try {
                workflowClient.terminateWorkflow(session.getWorkflowId(), "QA reset - discard demo session");
            } catch (Exception e) {
                // Workflow có thể đã COMPLETED/FAILED/không tồn tại — bỏ qua, không chặn reset.
                log.debug("Không terminate được workflow {} (có thể đã kết thúc): {}",
                        session.getWorkflowId(), e.getMessage());
            }
        }
        auditLogRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        var keys = redisTemplate.keys("onboarding:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        log.warn("[DEBUG] Reset toàn bộ session mapping + audit log + Redis onboarding:* + terminate {} workflow(s) trên Orkes Cloud",
                allSessions.size());
    }
}