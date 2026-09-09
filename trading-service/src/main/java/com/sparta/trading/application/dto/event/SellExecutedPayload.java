package com.sparta.trading.application.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SellExecutedPayload(
        UUID executionId,
        UUID orderId,
        UUID positionId,
        Long stockId,
        String stockCode,
        String stockName,
        int quantity,
        BigDecimal executedPrice,
        int positionQuantityAfter,
        BigDecimal positionAverageEntryPrice,
        BigDecimal plannedStopLossPrice,
        BigDecimal executionRealizedProfit,
        OffsetDateTime quoteTimestamp,
        OffsetDateTime executedAt
) {
}
