package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.PositionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionDetailResponse(
        UUID positionId,
        Long stockId,
        String symbol,
        String name,
        PositionStatus status,
        int quantity,
        BigDecimal averageEntryPrice,
        BigDecimal currentPrice,
        BigDecimal unrealizedProfit,
        BigDecimal plannedStopLossPrice,
        String investmentReason,
        int totalBuyQuantity,
        int totalSellQuantity,
        BigDecimal realizedProfit,
        Instant openedAt,
        Instant closedAt

) {
}
