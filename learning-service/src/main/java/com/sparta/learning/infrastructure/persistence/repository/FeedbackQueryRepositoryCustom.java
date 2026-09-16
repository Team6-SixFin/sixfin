package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.result.FeedbackListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * QueryDSL로 구현하는 피드백 동적 조회 계약
 */
public interface FeedbackQueryRepositoryCustom {

    /** 목록은 응답에 필요한 컬럼만 조회한다. */
    Page<FeedbackListRow> findListRows(FeedbackListQuery query, Pageable pageable);

    /** 포지션별 목록도 응답에 필요한 컬럼만 조회한다. */
    List<FeedbackListRow> findListRowsByPosition(UUID userId, UUID positionId);
}
