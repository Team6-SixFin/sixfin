package com.sparta.trading.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.*;
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
@Table(name="p_outbox_events")
public class OutboxEvents extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name="event_id", nullable = false)
    private UUID eventId;

    @Column(name ="event_type",nullable = false,length = 50)
    private String eventType;

    @Column(name ="event_version", nullable = false)
    private Integer eventVersion;

    @Column(name ="aggregate_type",nullable = false,length = 50)
    private String aggregateType;

    @Column(name="aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name ="partition_key",nullable = false,length = 50)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name ="retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "last_error",  length = 500)
    private String lastError;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
