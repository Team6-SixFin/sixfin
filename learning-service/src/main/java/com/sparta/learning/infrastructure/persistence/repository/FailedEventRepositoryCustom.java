package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.query.FailedEventListQuery;
import com.sparta.learning.domain.entity.FailedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/* QueryDSL로 구현하는 실패 이벤트 동적 조회 계약 */
public interface FailedEventRepositoryCustom {

    Page<FailedEvent> findAllByQuery(FailedEventListQuery query, Pageable pageable);
}
