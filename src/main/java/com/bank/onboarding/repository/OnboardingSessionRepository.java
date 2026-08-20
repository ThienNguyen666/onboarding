package com.bank.onboarding.repository;

import com.bank.onboarding.entity.OnboardingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, String> {
    Optional<OnboardingSession> findTopByPhoneOrderByCreatedAtDesc(String phone);
}
