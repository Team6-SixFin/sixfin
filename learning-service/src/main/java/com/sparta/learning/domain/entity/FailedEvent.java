package com.sparta.learning.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.model.FailedEventStatus;
import com.sparta.learning.domain.model.TradeEventType;
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

/* 재시도를 소진해 DLT로 보관된 이벤트 */
@Getter
@Entity
@Table(
        name = "failed_events",
        indexes = {
                @Index(name = "idx_failed_event_status", columnList = "status"),
                @Index(name = "idx_failed_event_event_id", columnList = "event_id"),
                @Index(name = "idx_failed_event_user_id", columnList = "user_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 이벤트가 서로 다른 원인으로 여러 번 실패할 수 있어 UNIQUE를 걸지 않음
    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50)
    private TradeEventType eventType;

    @Column(name = "user_id")
    private UUID userId;

    // 재처리는 이 원본으로 수집부터 다시 실행하므로 다른 테이블과 조인하지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    // 원본 위치는 DLT 헤더에서 옮겨온다 (Kafka에서 직접 확인할 때 사용)
    @Column(name = "original_topic", length = 100)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FailedEventStatus status;

    @Column(name = "last_retried_at")
    private OffsetDateTime lastRetriedAt;

    @Builder
    private FailedEvent(
            UUID eventId,
            TradeEventType eventType,
            UUID userId,
            JsonNode payload,
            String failureReason,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.userId = userId;
        this.payload = payload;
        this.failureReason = failureReason;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.retryCount = 0;
        this.status = FailedEventStatus.PENDING;
    }

    // 재처리 성공
    public void resolve() {
        this.status = FailedEventStatus.RESOLVED;
        this.retryCount++;
        this.lastRetriedAt = OffsetDateTime.now();
    }

    /*
     * 재처리 실패. 원인을 갱신해 다음 시도 때 최신 사유를 보게 한다.
     * 다시 시도할 수 있어야 하므로 선점했던 상태를 PENDING으로 되돌린다.
     */
    public void recordRetryFailure(String failureReason) {
        this.status = FailedEventStatus.PENDING;
        this.failureReason = failureReason;
        this.retryCount++;
        this.lastRetriedAt = OffsetDateTime.now();
    }

    public boolean isResolved() {
        return this.status == FailedEventStatus.RESOLVED;
    }
}
