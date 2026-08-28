package com.sparta.learning.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.model.TradeEventType;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "consumed_events",
        indexes = @Index(name = "idx_consumed_event_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private TradeEventType eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "consumed_at", nullable = false, updatable = false)
    private OffsetDateTime consumedAt;

    @Builder
    private ConsumedEvent(
            UUID eventId,
            TradeEventType eventType,
            int eventVersion,
            UUID userId,
            JsonNode payload,
            OffsetDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion < 1 ? 1 : eventVersion;
        this.userId = userId;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
