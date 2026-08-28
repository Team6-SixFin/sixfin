package com.sparta.learning.infrastructure.messaging.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SellExecutedPayload(
        @NotNull UUID executionId,
        @NotNull UUID orderId,
        @NotNull UUID positionId,
        @NotNull @Positive Long stockId,
        @NotBlank String stockCode,
        @NotBlank String stockName,
        @Positive int quantity,
        @NotNull @Positive BigDecimal executedPrice,
        @PositiveOrZero int positionQuantityAfter,
        @NotNull @Positive BigDecimal positionAverageEntryPrice,
        BigDecimal plannedStopLossPrice,
        @NotNull BigDecimal executionRealizedProfit,
        @NotNull OffsetDateTime quoteTimestamp,
        @NotNull OffsetDateTime executedAt
) {
}
