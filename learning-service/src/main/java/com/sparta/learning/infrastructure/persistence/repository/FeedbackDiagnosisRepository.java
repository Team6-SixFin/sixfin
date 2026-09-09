package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.FeedbackDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackDiagnosisRepository extends JpaRepository<FeedbackDiagnosis, Long> {
    // 피드백 ID로 기존 매핑 목록 조회
    List<FeedbackDiagnosis> findAllByFeedbackId(Long feedbackId);
}