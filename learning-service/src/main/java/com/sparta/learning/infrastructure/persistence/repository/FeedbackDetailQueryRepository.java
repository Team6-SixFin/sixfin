package com.sparta.learning.infrastructure.persistence.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.model.ResourceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sparta.learning.domain.entity.QDiagnosisResult.diagnosisResult;
import static com.sparta.learning.domain.entity.QExecutionSnapshot.executionSnapshot;
import static com.sparta.learning.domain.entity.QFeedback.feedback;
import static com.sparta.learning.domain.entity.QFeedbackDiagnosis.feedbackDiagnosis;
import static com.sparta.learning.domain.entity.QFeedbackResource.feedbackResource;
import static com.sparta.learning.domain.entity.QLearningResource.learningResource;

/**
 * 피드백 상세 화면에 필요한 데이터를 QueryDSL로 조회
 */
@Repository
@RequiredArgsConstructor
public class FeedbackDetailQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Optional<Feedback> findFeedback(Long feedbackId, UUID userId) {
        return Optional.ofNullable(queryFactory
                .selectFrom(feedback)
                .where(
                        feedback.id.eq(feedbackId),
                        feedback.userId.eq(userId)
                )
                .fetchOne());
    }

    public ExecutionSnapshot findFirstExecution(UUID positionId) {
        return queryFactory
                .selectFrom(executionSnapshot)
                .where(executionSnapshot.positionId.eq(positionId))
                .orderBy(executionSnapshot.executedAt.asc(), executionSnapshot.id.asc())
                .fetchFirst();
    }

    public List<DiagnosisResult> findDiagnoses(Long feedbackId) {
        return queryFactory
                .select(diagnosisResult)
                .from(feedbackDiagnosis)
                .join(feedbackDiagnosis.diagnosisResult, diagnosisResult)
                .leftJoin(diagnosisResult.executionSnapshot, executionSnapshot).fetchJoin()
                .where(feedbackDiagnosis.feedback.id.eq(feedbackId))
                .orderBy(feedbackDiagnosis.id.asc())
                .fetch();
    }

    public List<FeedbackResource> findActiveResources(Long feedbackId) {
        return queryFactory
                .select(feedbackResource)
                .from(feedbackResource)
                .join(feedbackResource.learningResource, learningResource).fetchJoin()
                .where(
                        feedbackResource.feedback.id.eq(feedbackId),
                        learningResource.status.eq(ResourceStatus.ACTIVE)
                )
                .orderBy(feedbackResource.displayOrder.asc(), feedbackResource.id.asc())
                .fetch();
    }
}
