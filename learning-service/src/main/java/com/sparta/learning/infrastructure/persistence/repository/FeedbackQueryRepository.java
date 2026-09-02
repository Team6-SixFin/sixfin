package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 피드백 생성 담당 Repository와 역할이 섞이지 않도록 조회 기능에서 사용하는 Repository
 */
public interface FeedbackQueryRepository
        extends JpaRepository<Feedback, Long>, FeedbackQueryRepositoryCustom {
}
