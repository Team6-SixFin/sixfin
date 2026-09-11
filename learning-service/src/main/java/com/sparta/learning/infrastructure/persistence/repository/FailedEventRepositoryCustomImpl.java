package com.sparta.learning.infrastructure.persistence.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.learning.application.dto.query.FailedEventListQuery;
import com.sparta.learning.domain.entity.FailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.sparta.learning.domain.entity.QFailedEvent.failedEvent;

/* 실패 이벤트 목록의 선택 조건, 정렬, 페이징을 QueryDSL로 처리 */
@RequiredArgsConstructor
public class FailedEventRepositoryCustomImpl implements FailedEventRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<FailedEvent> findAllByQuery(FailedEventListQuery query, Pageable pageable) {
        BooleanBuilder conditions = createConditions(query);

        List<FailedEvent> content = queryFactory
                .selectFrom(failedEvent)
                .where(conditions)
                // 최근 실패부터 확인하며, 생성 시각이 같아도 순서가 틀리지 않도록 ID를 보조 기준으로
                .orderBy(failedEvent.createdAt.desc(), failedEvent.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(failedEvent.count())
                .from(failedEvent)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    // 관리자 전용 조회라 사용자 범위 조건이 없다. userId는 선택 필터로만 사용함
    private BooleanBuilder createConditions(FailedEventListQuery query) {
        BooleanBuilder conditions = new BooleanBuilder();

        if (query.status() != null) {
            conditions.and(failedEvent.status.eq(query.status()));
        }
        if (query.eventType() != null) {
            conditions.and(failedEvent.eventType.eq(query.eventType()));
        }
        if (query.userId() != null) {
            conditions.and(failedEvent.userId.eq(query.userId()));
        }
        if (query.from() != null) {
            conditions.and(failedEvent.createdAt.goe(query.from()));
        }
        if (query.to() != null) {
            conditions.and(failedEvent.createdAt.loe(query.to()));
        }

        return conditions;
    }
}
