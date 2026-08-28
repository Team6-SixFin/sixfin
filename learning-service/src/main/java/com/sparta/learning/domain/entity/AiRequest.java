package com.sparta.learning.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.model.AiRequestStatus;
import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "ai_requests",
        indexes = @Index(name = "idx_ai_requests_feedback", columnList = "feedback_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_json", columnDefinition = "jsonb")
    private JsonNode outputJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiRequestStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    private AiRequest(
            Feedback feedback,
            String requestId,
            String modelName,
            String promptVersion,
            JsonNode inputJson,
            JsonNode outputJson,
            AiRequestStatus status,
            String errorMessage
    ) {
        this.feedback = feedback;
        this.requestId = requestId;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.inputJson = inputJson;
        this.outputJson = outputJson;
        this.status = status;
        this.errorMessage = errorMessage;
        this.completedAt = OffsetDateTime.now();
    }

    public static AiRequest success(
            Feedback feedback,
            String requestId,
            String modelName,
            String promptVersion,
            JsonNode inputJson,
            JsonNode outputJson
    ) {
        return new AiRequest(
                feedback, requestId, modelName, promptVersion,
                inputJson, outputJson, AiRequestStatus.SUCCESS, null
        );
    }

    public static AiRequest failed(
            Feedback feedback,
            String requestId,
            String modelName,
            String promptVersion,
            JsonNode inputJson,
            String errorMessage
    ) {
        return new AiRequest(
                feedback, requestId, modelName, promptVersion,
                inputJson, null, AiRequestStatus.FAILED, errorMessage
        );
    }

    public static AiRequest fallback(
            Feedback feedback,
            String requestId,
            String modelName,
            String promptVersion,
            JsonNode inputJson,
            JsonNode outputJson,
            String errorMessage
    ) {
        return new AiRequest(
                feedback, requestId, modelName, promptVersion,
                inputJson, outputJson, AiRequestStatus.FALLBACK, errorMessage
        );
    }
}
