package com.sparta.learning.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "feedbacks",
        indexes = {
                // === 목록 조회의 정렬까지 인덱스에서 끝내기 위한 복합 인덱스 ===
                //
                // [Why created_at이 id보다 앞인가] 목록 정렬이 ORDER BY created_at DESC, id DESC 다.
                // 선두 컬럼으로 범위를 좁히고 그 다음 컬럼들이 정렬 순서와 일치해야
                // 실행 계획에서 Sort 노드가 사라지고 Limit이 앞에서 멈출 수 있다.
                // (user_id, id) 로는 정렬을 만족시키지 못해 1만 행을 읽고 정렬하게 된다.
                //
                // [Why DESC 인덱스를 안 만드나] 정렬 방향이 두 컬럼 모두 DESC 로 같으므로
                // PostgreSQL B-tree가 오름차순 인덱스를 Backward Index Scan 으로 그대로 쓴다.
                // JPA @Index 가 정렬 방향을 지정하지 못하는 문제도 이걸로 해결된다.
                @Index(name = "idx_feedback_user_created_id",
                        columnList = "user_id, created_at, id"),

                // type / status 필터 조회. 필터 컬럼을 선두에 둬야 범위를 좁힌 뒤
                // 남은 (created_at, id) 로 정렬까지 인덱스에서 끝난다.
                @Index(name = "idx_feedback_user_type_created",
                        columnList = "user_id, feedback_type, created_at, id"),
                @Index(name = "idx_feedback_user_status_created",
                        columnList = "user_id, status, created_at, id"),

                // positionId 필터 조회와 포지션별 피드백 타임라인(created_at ASC)을 함께 커버한다.
                // position_id 는 선택도가 높아 선두로 충분하고, user_id 는 소수 행에 대한 필터로 처리된다.
                @Index(name = "idx_feedback_position_created",
                        columnList = "position_id, created_at, id"),

                @Index(name = "idx_feedback_user_id", columnList = "user_id"),
                @Index(name = "idx_feedback_position_id", columnList = "position_id"),
                @Index(name = "idx_feedback_type", columnList = "feedback_type"),
                @Index(name = "idx_feedback_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_key", nullable = false, unique = true, length = 150)
    private String feedbackKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 30)
    private FeedbackType feedbackType;

    @Column(name = "based_on_execution_id")
    private UUID basedOnExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbackStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private JsonNode content;

    @Column(name = "ai_used", nullable = false)
    private boolean aiUsed;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Builder
    private Feedback(
            String feedbackKey,
            UUID userId,
            UUID positionId,
            FeedbackType feedbackType,
            UUID basedOnExecutionId
    ) {
        this.feedbackKey = feedbackKey;
        this.userId = userId;
        this.positionId = positionId;
        this.feedbackType = feedbackType;
        this.basedOnExecutionId = basedOnExecutionId;
        this.status = FeedbackStatus.PENDING;
        this.aiUsed = false;
    }

    public void complete(JsonNode content, boolean aiUsed, String promptVersion) {
        this.status = FeedbackStatus.COMPLETED;
        this.content = content;
        this.aiUsed = aiUsed;
        this.promptVersion = promptVersion;
        this.failureReason = null;
        this.completedAt = OffsetDateTime.now();
    }

    public void completeWithFallback(JsonNode content, String failureReason) {
        this.status = FeedbackStatus.COMPLETED;
        this.content = content;
        this.aiUsed = false;
        this.failureReason = failureReason;
        this.completedAt = OffsetDateTime.now();
    }

    public void fail(String failureReason) {
        this.status = FeedbackStatus.FAILED;
        this.failureReason = failureReason;
        this.completedAt = OffsetDateTime.now();
    }

    public void updateStatus(FeedbackStatus status) {
        this.status = status;
    }
}
