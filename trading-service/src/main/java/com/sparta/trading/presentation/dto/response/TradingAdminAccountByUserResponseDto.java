package com.sparta.trading.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradingAdminAccountByUserResponseDto(
        UUID accountId,
        UUID userId,
        BigDecimal cashBalance,
        BigDecimal orderableAmount,
        BigDecimal initialDeposit,
        BigDecimal evaluationAmount,
        BigDecimal totalAsset,
        Instant valuationAt,
        List<PositionDto> positions,
        Instant cratedAt
) {
    public record PositionDto(
            UUID positionId,
            String symbol,
            Integer quantity,
            BigDecimal averageEntryPrice,
            BigDecimal currentPrice,
            BigDecimal unrealizedProfit
    ) {
    }
}
