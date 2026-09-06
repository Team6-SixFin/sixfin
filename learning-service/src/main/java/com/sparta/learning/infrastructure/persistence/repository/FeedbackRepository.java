package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// 피드백 저장 및 변경을 위한 JPA Repository
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByFeedbackKey(String feedbackKey);

    // [리뷰 반영 추가] 특정 포지션의 가장 최근에 완료된(content가 존재하는) 피드백 조회
    Optional<Feedback> findTopByPositionIdAndContentIsNotNullOrderByIdDesc(UUID positionId);
}