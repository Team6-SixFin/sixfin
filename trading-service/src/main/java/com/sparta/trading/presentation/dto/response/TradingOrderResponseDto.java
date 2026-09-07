package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.Stocks;

import java.time.Instant;
import java.util.UUID;

public record TradingOrderResponseDto (
    UUID orderId,
    UUID positionId,
    String symbol,
    String name,
    String side,
    String orderType,
    Integer quantity,
    String status,
    String rejectReason,
    Instant marketTime,
    Instant createdAt
) {
    public static TradingOrderResponseDto from(Stocks stock, Orders order){
        return new TradingOrderResponseDto(
                order.getId(),
                order.getPositionId(),
                stock.getSymbol(),
                stock.getName(),
                order.getSide(),
                order.getOrderType(),
                order.getQuantity(),
                order.getStatus(),
                order.getRejectReason(),
                order.getMarketTime(),
                order.getCreatedAt()
        );
    }
}
