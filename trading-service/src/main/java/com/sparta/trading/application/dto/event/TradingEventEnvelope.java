package com.sparta.trading.application.dto.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TradingEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        UUID userId,
        BuyExecutedPayload payload
) {
}
