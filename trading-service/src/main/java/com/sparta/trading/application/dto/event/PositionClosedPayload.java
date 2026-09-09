package com.sparta.trading.application.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PositionClosedPayload(
        UUID positionId,
        Long stockId,
        String stockCode,
        String stockName,
        long totalQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal averageExitPrice,
        BigDecimal stopLossPrice,
        BigDecimal realizedProfit,
        BigDecimal realizedReturnRate,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt
) {
}
