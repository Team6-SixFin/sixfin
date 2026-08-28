package com.sparta.learning.infrastructure.messaging.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PositionClosedPayload(
        @NotNull UUID positionId,
        @NotNull @Positive Long stockId,
        @NotBlank String stockCode,
        @NotBlank String stockName,
        @Positive long totalQuantity,
        @NotNull @Positive BigDecimal averageEntryPrice,
        @NotNull @Positive BigDecimal averageExitPrice,
        BigDecimal stopLossPrice,
        @NotNull BigDecimal realizedProfit,
        @NotNull BigDecimal realizedReturnRate,
        @NotNull OffsetDateTime openedAt,
        @NotNull OffsetDateTime closedAt
) {
}
