package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.PositionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionResponse(
        UUID positionId,
        String symbol,
        String name,
        PositionStatus status,
        int quantity,
        BigDecimal averageEntryPrice,
        BigDecimal currentPrice,
        BigDecimal unrealizedProfit,
        Instant openedAt,
        Instant closedAt
) {

}
