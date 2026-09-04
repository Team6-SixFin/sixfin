package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID requestId,
        UUID positionId,
        OrderStatus status,
        String rejectReason,
        ExecutionResponse execution,
        BigDecimal cashBalance,
        Instant marketTime,
        Long candleSeq
) {
    public record ExecutionResponse(
            UUID executionId,
            int quantity,
            BigDecimal executedPrice,
            BigDecimal executedAmount,
            Instant executedAt
    ) {
    }
}
