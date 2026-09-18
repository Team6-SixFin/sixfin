package com.sparta.learning.infrastructure.persistence.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.result.FeedbackListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.UUID;

import static com.sparta.learning.domain.entity.QFeedback.feedback;

/**
 * 피드백 목록의 선택 조건, 정렬, 페이징을 QueryDSL로 처리

 */
@RequiredArgsConstructor
public class FeedbackQueryRepositoryCustomImpl implements FeedbackQueryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * [Why limit + 1] 요청 개수보다 1건 많게 읽고, 초과분의 존재 자체로 hasNext를 판단한다.
     * 조건 전체를 세는 count 쿼리 한 번이 통째로 사라진다.
     * 초과분은 응답에서 잘라내므로 페이지 크기는 그대로 유지된다.
     *
     * [정렬을 바꾸지 않는 이유] ORDER BY는 idx_feedback_user_created_id 등
     * 복합 인덱스의 컬럼 순서와 정확히 맞춰져 있다.
     * 여기서 정렬 키를 바꾸면 인덱스가 다시 안 먹고, 이번 PR의 개선 효과가
     * count 제거 때문인지 정렬 변경 때문인지 분리할 수 없게 된다.
     *
     * [남은 비용] OFFSET은 그대로다. 뒤 페이지는 건너뛸 행을 여전히 읽는다.
     * 커서 페이징으로만 해결되며, 이번 측정 결과를 보고 진행 여부를 판단한다.
     */
    @Override
    public Slice<FeedbackListRow> findListRows(FeedbackListQuery query, Pageable pageable) {
        BooleanBuilder conditions = createConditions(query);
        int pageSize = pageable.getPageSize();

        List<FeedbackListRow> rows = queryFactory
                .select(listRow())
                .from(feedback)
                .where(conditions)
                // 생성 시각이 같은 경우에도 결과 순서가 바뀌지 않도록 ID를 보조 정렬 기준으로 사용
                .orderBy(feedback.createdAt.desc(), feedback.id.desc())
                .offset(pageable.getOffset())
                .limit(pageSize + 1L)
                .fetch();

        boolean hasNext = rows.size() > pageSize;
        List<FeedbackListRow> content = hasNext ? rows.subList(0, pageSize) : rows;

        return new SliceImpl<>(content, pageable, hasNext);
    }

    @Override
    public List<FeedbackListRow> findListRowsByPosition(UUID userId, UUID positionId) {
        return queryFactory
                .select(listRow())
                .from(feedback)
                .where(
                        feedback.userId.eq(userId),
                        feedback.positionId.eq(positionId)
                )
                // 최초 매수부터 종료 회고까지 시간 흐름대로
                .orderBy(feedback.createdAt.asc(), feedback.id.asc())
                .fetch();
    }

    /** 전체 목록과 포지션별 목록이 같은 컬럼을 조회하므로 프로젝션을 공유한다. */
    private static ConstructorExpression<FeedbackListRow> listRow() {
        return Projections.constructor(
                FeedbackListRow.class,
                feedback.id,
                feedback.positionId,
                feedback.feedbackType,
                feedback.status,
                summary(),
                feedback.aiUsed,
                feedback.basedOnExecutionId,
                feedback.createdAt,
                feedback.completedAt
        );
    }

    /** content JSONB에서 summary 한 필드만 DB에서 추출한다. */
    private static StringExpression summary() {
        return Expressions.stringTemplate("json_value({0}, '$.summary')", feedback.content);
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
