package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.Stocks;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingOrderDetailResponseDto(
        UUID orderId,
        UUID requestId,
        UUID positionId,
        Long stockId,
        String symbol,
        String name,
        String side,
        String orderType,
        Integer quantity,
        String status,
        String rejectReason,
        BigDecimal plannedStopLossPrice,
        String investmentReason,
        Instant marketTime,
        Long candleSeq,
        ExecutionDetail execution
) {

    public record ExecutionDetail(
            UUID executionId,
            BigDecimal executedPrice,
            Integer executedQuantity,
            BigDecimal executedAmount,
            BigDecimal realizedProfit,
            Instant executedAt
    ) {
        public static ExecutionDetail from(Executions execution) {
            return new ExecutionDetail(
                    execution.getId(),
                    execution.getExecutedPrice(),
                    execution.getExecutedQuantity(),
                    execution.getExecutedAmount(),
                    execution.getRealizedProfit(),
                    execution.getMarketTime()
            );
        }
    }

    public static TradingOrderDetailResponseDto from(Stocks stock, Orders order, Executions execution) {
        return new TradingOrderDetailResponseDto(
                order.getId(),
                order.getRequestId(),
                order.getPositionId(),
                order.getStockId(),
                stock.getSymbol(),
                stock.getName(),
                order.getSide(),
                order.getOrderType(),
                order.getQuantity(),
                order.getStatus(),
                order.getRejectReason(),
                order.getPlannedStopLossPrice(),
                order.getInvestmentReason(),
                order.getMarketTime(),
                order.getCandleSeq(),
                execution == null ? null : ExecutionDetail.from(execution)
        );
    }
}
