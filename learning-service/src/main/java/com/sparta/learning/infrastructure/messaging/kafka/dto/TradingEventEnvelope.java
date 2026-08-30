package com.sparta.learning.infrastructure.messaging.kafka.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.model.TradeEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TradingEventEnvelope(
        @NotNull UUID eventId,
        @NotNull TradeEventType eventType,
        @Min(1) int eventVersion,
        @NotNull OffsetDateTime occurredAt,
        @NotNull UUID userId,
        @NotNull JsonNode payload
) {
}
