package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.FailedEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "재시도를 소진해 DLT로 보관된 이벤트")
public record FailedEventListItemResponse(

        @Schema(description = "실패 이벤트 ID. 재처리 API에 사용합니다.", example = "1024")
        Long id,

        @Schema(description = "Trading이 발행한 원본 이벤트 ID")
        UUID eventId,

        @Schema(description = "이벤트 종류", example = "BUY_EXECUTED")
        String eventType,

        @Schema(description = "이벤트 소유자")
        UUID userId,

        @Schema(description = "재처리 상태", example = "PENDING")
        String status,

        @Schema(description = "마지막 실패 원인", example = "java.lang.IllegalStateException: 손절가 계산 실패")
        String failureReason,

        @Schema(description = "재처리 시도 횟수", example = "0")
        int retryCount,

        @Schema(description = "원본 토픽", example = "trade-events.v1")
        String originalTopic,

        @Schema(description = "원본 파티션", example = "2")
        Integer originalPartition,

        @Schema(description = "원본 offset", example = "1523")
        Long originalOffset,

        @Schema(description = "마지막 재처리 시각")
        OffsetDateTime lastRetriedAt,

        @Schema(description = "실패 기록 시각")
        OffsetDateTime createdAt
) {

    // payload는 크기가 커서 목록에 담지 않는다 (재처리에만 사용)
    public static FailedEventListItemResponse from(FailedEvent failedEvent) {
        return new FailedEventListItemResponse(
                failedEvent.getId(),
                failedEvent.getEventId(),
                failedEvent.getEventType().name(),
                failedEvent.getUserId(),
                failedEvent.getStatus().name(),
                failedEvent.getFailureReason(),
                failedEvent.getRetryCount(),
                failedEvent.getOriginalTopic(),
                failedEvent.getOriginalPartition(),
                failedEvent.getOriginalOffset(),
                failedEvent.getLastRetriedAt(),
                failedEvent.getCreatedAt()
        );
    }
}
