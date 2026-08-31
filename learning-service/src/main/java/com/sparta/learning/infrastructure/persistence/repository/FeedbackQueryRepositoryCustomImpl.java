package com.sparta.learning.infrastructure.persistence.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.domain.entity.Feedback;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.sparta.learning.domain.entity.QFeedback.feedback;

/**
 * 피드백 목록의 선택 조건, 정렬, 페이징을 QueryDSL로 처리

 */
@RequiredArgsConstructor
public class FeedbackQueryRepositoryCustomImpl implements FeedbackQueryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Feedback> findAllByQuery(FeedbackListQuery query, Pageable pageable) {
        BooleanBuilder conditions = createConditions(query);

        List<Feedback> content = queryFactory
                .selectFrom(feedback)
                .where(conditions)
                // 생성 시각이 같은 경우에도 결과 순서가 바뀌지 않도록 ID를 보조 정렬 기준으로 사용
                .orderBy(feedback.createdAt.desc(), feedback.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(feedback.count())
                .from(feedback)
                .where(conditions)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanBuilder createConditions(FeedbackListQuery query) {
        BooleanBuilder conditions = new BooleanBuilder();

        // 사용자 범위 조건은 선택 조건이 아니라 필수 조건
        conditions.and(feedback.userId.eq(query.userId()));

        if (query.feedbackType() != null) {
            conditions.and(feedback.feedbackType.eq(query.feedbackType()));
        }
        if (query.positionId() != null) {
            conditions.and(feedback.positionId.eq(query.positionId()));
        }
        if (query.status() != null) {
            conditions.and(feedback.status.eq(query.status()));
        }

        return conditions;
    }
}
