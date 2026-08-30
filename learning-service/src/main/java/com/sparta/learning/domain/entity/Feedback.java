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
}
