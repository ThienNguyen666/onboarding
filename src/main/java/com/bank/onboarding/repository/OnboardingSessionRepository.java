package com.bank.onboarding.repository;

import com.bank.onboarding.entity.OnboardingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, String> {
    Optional<OnboardingSession> findTopByPhoneAndIdNotOrderByCreatedAtDesc(String phone, String id);
    List<OnboardingSession> findTop20ByOrderByCreatedAtDesc();
}
