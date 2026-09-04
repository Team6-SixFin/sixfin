package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.entity.Stocks;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingExecutionResponseDto(
        UUID executionId,
        UUID orderId,
        UUID positionId,
        Long stockId,
        String symbol,
        String name,
        String side,
        BigDecimal executedPrice,
        Integer executedQuantity,
        BigDecimal executedAmount,
        BigDecimal realizedProfit,
        Instant marketTime,
        Long candleSeq,
        Instant createdAt
) {

    public static TradingExecutionResponseDto from(Stocks stock, Executions execution) {
        return new TradingExecutionResponseDto(
                execution.getId(),
                execution.getOrderId(),
                execution.getPositionId(),
                execution.getStockId(),
                stock.getSymbol(),
                stock.getName(),
                execution.getSide(),
                execution.getExecutedPrice(),
                execution.getExecutedQuantity(),
                execution.getExecutedAmount(),
                execution.getRealizedProfit(),
                execution.getMarketTime(),
                execution.getCandleSeq(),
                execution.getCreatedAt()
        );
    }
}
