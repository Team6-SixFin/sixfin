package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.FailedEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "실패 이벤트 재처리 결과")
public record FailedEventRetryResponse(

        @Schema(description = "실패 이벤트 ID", example = "1024")
        Long id,

        @Schema(description = "Trading이 발행한 원본 이벤트 ID")
        UUID eventId,

        @Schema(description = "재처리 후 상태", example = "RESOLVED")
        String status,

        @Schema(description = "재처리 시도 횟수", example = "1")
        int retryCount,

        @Schema(description = "재처리 시각")
        OffsetDateTime lastRetriedAt
) {

    public static FailedEventRetryResponse from(FailedEvent failedEvent) {
        return new FailedEventRetryResponse(
                failedEvent.getId(),
                failedEvent.getEventId(),
                failedEvent.getStatus().name(),
                failedEvent.getRetryCount(),
                failedEvent.getLastRetriedAt()
        );
    }
}
