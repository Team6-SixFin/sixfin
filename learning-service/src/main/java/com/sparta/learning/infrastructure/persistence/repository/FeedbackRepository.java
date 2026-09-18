package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 피드백 저장 및 변경을 위한 JPA Repository
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByFeedbackKey(String feedbackKey);

    // [리뷰 반영 추가] 특정 포지션의 가장 최근에 완료된 피드백을 완료 시각(completedAt) 기준으로 조회
    Optional<Feedback> findTopByPositionIdAndStatusOrderByCompletedAtDesc(UUID positionId, FeedbackStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feedback from Feedback feedback where feedback.id = :feedbackId")
    Optional<Feedback> findByIdForUpdate(@Param("feedbackId") Long feedbackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Feedback feedback
               set feedback.status = com.sparta.learning.domain.model.FeedbackStatus.PROCESSING,
                   feedback.attemptCount = feedback.attemptCount + 1
             where feedback.id = :feedbackId
               and feedback.status = :expectedStatus
            """)
    int claimForProcessing(
            @Param("feedbackId") Long feedbackId,
            @Param("expectedStatus") FeedbackStatus expectedStatus);

    //점유만 남고 끊긴 PROCESSING 을 FAILED 로 되돌려 다시 시도할 수 있게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Feedback feedback
               set feedback.status = com.sparta.learning.domain.model.FeedbackStatus.FAILED,
                   feedback.failureReason = :failureReason,
                   feedback.completedAt = :releasedAt
             where feedback.id = :feedbackId
               and feedback.status = com.sparta.learning.domain.model.FeedbackStatus.PROCESSING
               and feedback.content is null
            """)
    int releaseStaleProcessing(
            @Param("feedbackId") Long feedbackId,
            @Param("failureReason") String failureReason,
            @Param("releasedAt") OffsetDateTime releasedAt);

    // 재처리 대상을 오래된 순으로 조회한다
    @Query("""
            select feedback
              from Feedback feedback
             where feedback.feedbackType in (:feedbackTypes)
               and feedback.attemptCount < :maxAttempts
               and (
                     (feedback.status = com.sparta.learning.domain.model.FeedbackStatus.FAILED
                      and feedback.failureReason in (:retryableReasons))
                  or (feedback.status = com.sparta.learning.domain.model.FeedbackStatus.PROCESSING
                      and feedback.content is null
                      and coalesce(feedback.completedAt, feedback.createdAt) < :staleBefore)
                   )
             order by coalesce(feedback.completedAt, feedback.createdAt) asc
            """)
    List<Feedback> findRetryTargets(
            @Param("feedbackTypes") Collection<FeedbackType> feedbackTypes,
            @Param("retryableReasons") Collection<String> retryableReasons,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") OffsetDateTime staleBefore,
            Pageable pageable);
}
