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
        this.lastError = errorMessage;
        if (retryCount >= maxRetry) {
            setStatus(OutboxStatus.FAILED);
        }
    }
}
