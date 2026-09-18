package com.sparta.trading.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.trading.global.entity.AuditableEntity;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_outbox_events", schema = "trading_service")
public class OutboxEvents extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "partition_key", nullable = false, length = 50)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    // 현재 매매 흐름에서는 quote.marketTime을 톨해 저장됨.
    // 같은 seq의 매매는 같은 marketTime을 가짐.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Setter
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Setter
    @Column(name = "last_error", length = 500)
    private String lastError;

    private OutboxEvents(UUID eventId, String aggregateType, UUID aggregateId, String eventType,
                         UUID userId, JsonNode payload, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = 1;
        this.partitionKey = userId.toString();
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.occurredAt = occurredAt;
        this.retryCount = 0;
        initializeAudit(userId);
    }

    // 매수 체결 완료 이벤트를 Outbox 이벤트로 생성
    public static OutboxEvents buyExecuted(UUID eventId, UUID executionId, UUID userId,
                                            JsonNode payload, Instant occurredAt) {
        return new OutboxEvents(eventId, "EXECUTION", executionId, "BUY_EXECUTED",
                userId, payload, occurredAt);
    }

    // 매도 체결 완료 이벤트를 Outbox 이벤트로 생성
    public static OutboxEvents sellExecuted(UUID eventId, UUID executionId, UUID userId,
                                             JsonNode payload, Instant occurredAt) {
        return new OutboxEvents(eventId, "EXECUTION", executionId, "SELL_EXECUTED",
                userId, payload, occurredAt);
    }

    // 포지션 종료 이벤트를 Outbox 이벤트로 생성
    public static OutboxEvents positionClosed(UUID eventId, UUID positionId, UUID userId,
                                               JsonNode payload, Instant occurredAt) {
        return new OutboxEvents(eventId, "POSITION", positionId, "POSITION_CLOSED",
                userId, payload, occurredAt);
    }

    public void setStatus(OutboxStatus next){
        if(!this.status.validateNext(next)){
            throw new CustomException(TradingErrorCode.INVALID_TRANSITION_OF_OUTBOX_STATUS);
        }
        this.status = next;
    }

    public int addRetryCount(){
        return ++retryCount;
    }

    public void markPublished(Instant publishedAt) {
        setStatus(OutboxStatus.PUBLISHED);
        this.publishedAt = publishedAt;
    }

    public void markFailedAttempt(String errorMessage, int maxRetry) {
        addRetryCount();
        recordError(errorMessage);
        // 이미 FAILED인 건(관리자 재발행 실패 등)은 자기 자신 전이를 다시 시도하지 않는다.
        if (retryCount >= maxRetry && this.status != OutboxStatus.FAILED) {
            setStatus(OutboxStatus.FAILED);
        }
    }

    public void failManualRetry(String errorMessage, boolean countAttempt) {
        if (status != OutboxStatus.RETRYING) {
            throw new CustomException(TradingErrorCode.INVALID_TRANSITION_OF_OUTBOX_STATUS);
        }
        if (countAttempt) addRetryCount();
        recordError(errorMessage);
        setStatus(OutboxStatus.FAILED);
    }

    public void overwritePayload(JsonNode newPayload){
        // 관리자가 상세 payload를 고쳐도 Learning의 중복 제거 키와 가상 시간은 유지한다.
        if (newPayload == null || !newPayload.isObject()
                || !eventId.toString().equals(newPayload.path("eventId").asText())
                || !eventType.equals(newPayload.path("eventType").asText())
                || eventVersion != newPayload.path("eventVersion").asInt()
                || !partitionKey.equals(newPayload.path("userId").asText())
                || !newPayload.path("payload").isObject()
                || !matchesOccurredAt(newPayload.path("occurredAt").asText())) {
            throw new CustomException(TradingErrorCode.OUTBOX_PAYLOAD_MISMATCH);
        }
        this.payload = newPayload;
    }

    private boolean matchesOccurredAt(String candidate) {
        try {
            // PostgreSQL TIMESTAMPTZ의 마이크로초 정밀도에 맞춰 비교한다.
            return occurredAt.truncatedTo(ChronoUnit.MICROS)
                    .equals(Instant.parse(candidate).truncatedTo(ChronoUnit.MICROS));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private void recordError(String errorMessage) {
        this.lastError = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 500));
    }
}
