package com.sparta.trading.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.trading.global.entity.AuditableEntity;
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

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    private OutboxEvents(UUID eventId, UUID executionId, UUID userId, JsonNode payload, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = "EXECUTION";
        this.aggregateId = executionId;
        this.eventType = "BUY_EXECUTED";
        this.eventVersion = 1;
        this.partitionKey = userId.toString();
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.occurredAt = occurredAt;
        this.retryCount = 0;
        initializeAudit(userId);
    }

    public static OutboxEvents buyExecuted(UUID eventId, UUID executionId, UUID userId,
                                            JsonNode payload, Instant occurredAt) {
        return new OutboxEvents(eventId, executionId, userId, payload, occurredAt);
    }
}
