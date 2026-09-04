package com.sparta.trading.application.dto.command;

import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderType;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceOrderCommand(
        UUID requestId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        int quantity,
        BigDecimal plannedStopLossPrice,
        String investmentReason
) {
}
