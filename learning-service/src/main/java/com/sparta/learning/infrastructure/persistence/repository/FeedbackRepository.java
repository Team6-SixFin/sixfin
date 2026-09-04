package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 피드백 저장 및 변경을 위한 JPA Repository
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByFeedbackKey(String feedbackKey);

}