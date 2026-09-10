package com.sparta.learning.infrastructure.persistence.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.entity.QFeedback;
import com.sparta.learning.domain.entity.QFeedbackResource;
import com.sparta.learning.domain.model.ResourceStatus;
import com.sparta.learning.domain.model.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.sparta.learning.domain.entity.QLearningResource.learningResource;

/**
 * PostgreSQL에 보관된 학습 자료에서 공통 후보군을 구성
 * 실제 추천 시 사용자와 포지션의 추천 이력을 제외
 */
@Repository
@RequiredArgsConstructor
public class LearningResourceCandidateQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** Redis 미스 시 공통 후보 ID를 채우기 위한 조회 */
    public List<LearningResource> findPoolCandidates(
            String ruleCode,
            ResourceType resourceType,
            OffsetDateTime now,
            long limit
    ) {
        return queryFactory
                .selectFrom(learningResource)
                .where(
                        learningResource.ruleCode.eq(ruleCode),
                        learningResource.resourceType.eq(resourceType),
                        learningResource.status.eq(ResourceStatus.ACTIVE),
                        learningResource.expiresAt.gt(now)
                )
                .orderBy(
                        learningResource.publishedAt.desc().nullsLast(),
                        learningResource.searchedAt.desc(),
                        learningResource.id.desc()
                )
                .limit(limit)
                .fetch();
    }

    /**
     * Redis의 공통 후보 ID를 다시 DB에서 조회하면서 상태·만료와 개인별 중복을 검증
     */
    public List<LearningResource> findReusableCandidatesByIds(
            List<Long> candidateIds,
            UUID userId,
            UUID positionId,
            OffsetDateTime now,
            OffsetDateTime recentlyRecommendedSince
    ) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        QFeedback previousFeedback = new QFeedback("previousFeedback");
        QFeedbackResource previousFeedbackResource = new QFeedbackResource("previousFeedbackResource");

        BooleanExpression alreadyRecommended = JPAExpressions
                .selectOne()
                .from(previousFeedbackResource)
                .join(previousFeedbackResource.feedback, previousFeedback)
                .where(
                        previousFeedbackResource.learningResource.id.eq(learningResource.id),
                        previousFeedback.userId.eq(userId),
                        previousFeedback.positionId.eq(positionId)
                                .or(previousFeedbackResource.createdAt.goe(recentlyRecommendedSince))
                )
                .exists();

        return queryFactory
                .selectFrom(learningResource)
                .where(
                        learningResource.id.in(candidateIds),
                        learningResource.status.eq(ResourceStatus.ACTIVE),
                        learningResource.expiresAt.gt(now),
                        alreadyRecommended.not()
                )
                .fetch();
    }
}
