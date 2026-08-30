package com.sparta.learning.infrastructure.messaging.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BuyExecutedPayload(
        @NotNull UUID executionId,
        @NotNull UUID orderId,
        @NotNull UUID positionId,
        @JsonProperty("isNewPosition") boolean newPosition,
        @NotNull @Positive Long stockId,
        @NotBlank String stockCode,
        @NotBlank String stockName,
        @Positive int quantity,
        @NotNull @Positive BigDecimal executedPrice,
        @PositiveOrZero int positionQuantityAfter,
        @NotNull @Positive BigDecimal positionAverageEntryPrice,
        BigDecimal plannedStopLossPrice,
        String investmentReason,
        @NotNull @Valid MarketContextPayload marketContext,
        @NotNull OffsetDateTime executedAt
) {
}
