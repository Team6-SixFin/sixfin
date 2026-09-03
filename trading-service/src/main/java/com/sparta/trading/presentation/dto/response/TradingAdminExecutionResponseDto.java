package com.sparta.trading.presentation.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingAdminExecutionResponseDto(
        UUID executionId,
        UUID orderId,
        UUID positionId,
        UUID userId,
        String symbol,
        String side,
        BigDecimal executedPrice,
        Integer executedQuantity,
        BigDecimal executedAmount,
        BigDecimal avgEntryPriceAtExecution,
        BigDecimal realizedProfit,
        Long candleSeq,
        Instant marketTime,
        Instant createdAt
) {
}
