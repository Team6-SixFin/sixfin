package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.result.FeedbackListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

/**
 * QueryDSL로 구현하는 피드백 동적 조회 계약
 */
public interface FeedbackQueryRepositoryCustom {

    /** 목록은 응답에 필요한 컬럼만 조회한다.
     * 사용자 피드백 목록. 응답에 필요한 컬럼만, count 없이 다음 페이지 유무만 판단해 조회한다.
     * 관리자 목록과 달리 총 건수를 쓰지 않으므로 Page가 아니라 Slice를 반환한다.
     * */
    Slice<FeedbackListRow> findListRows(FeedbackListQuery query, Pageable pageable);

    /** 포지션별 목록도 응답에 필요한 컬럼만 조회한다. */
    List<FeedbackListRow> findListRowsByPosition(UUID userId, UUID positionId);
}
