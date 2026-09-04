package com.sparta.trading.presentation.dto.response;

import java.math.BigDecimal;
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
        String status,
        Integer retryCount,
        String lastError,
        Object payload,
        Instant occurredAt,
        Instant publishedAt,
        Long delayedSeconds
) {
}
