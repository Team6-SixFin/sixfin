package com.sparta.trading.application.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BuyExecutedPayload(
        UUID executionId,
        UUID orderId,
        UUID positionId,
        @JsonProperty("isNewPosition") boolean newPosition,
        Long stockId,
        String stockCode,
        String stockName,
        int quantity,
        BigDecimal executedPrice,
        int positionQuantityAfter,
        BigDecimal positionAverageEntryPrice,
        BigDecimal plannedStopLossPrice,
        String investmentReason,
        MarketContextPayload marketContext,
        OffsetDateTime executedAt
) {
}
