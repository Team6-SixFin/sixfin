package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.FeedbackDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackDiagnosisRepository extends JpaRepository<FeedbackDiagnosis, Long> {
}