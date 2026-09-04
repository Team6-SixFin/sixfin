package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {
    // 향후 특정 피드백 ID로 AI 요청 이력을 찾을 때 사용할 수 있는 메서드 (필요시 사용)
    Optional<AiRequest> findByFeedbackId(Long feedbackId);
}