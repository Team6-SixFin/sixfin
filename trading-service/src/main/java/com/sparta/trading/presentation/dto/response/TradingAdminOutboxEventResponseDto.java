package com.sparta.trading.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.UUID;

public record TradingAdminOutboxEventResponseDto(
        Long id,
        UUID eventId,
        String eventType,
        Integer eventVersion,
        String aggergateType,
        UUID aggergateId,
        String partitionKey,
        Enum status,
        Integer retryCount,
        String lastError,
        @JsonRawValue
        Object payload,
        Instant occurredAt,
        Instant publishedAt,
        Long delayedSeconds
) {
}
