package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.domain.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * QueryDSL로 구현하는 피드백 동적 조회 계약
 */
public interface FeedbackQueryRepositoryCustom {

    Page<Feedback> findAllByQuery(FeedbackListQuery query, Pageable pageable);
}
