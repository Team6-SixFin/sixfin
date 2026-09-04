package com.sparta.trading.presentation.dto.request;

import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 20) String symbol,
        @NotNull OrderSide side,
        OrderType orderType,
        @Positive int quantity,
        @DecimalMin(value = "0", inclusive = false) BigDecimal plannedStopLossPrice,
        @Size(max = 500) String investmentReason
) {
    public PlaceOrderRequest {
        if (orderType == null) {
            orderType = OrderType.MARKET;
        }
    }

    public PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(
                requestId,
                symbol,
                side,
                orderType,
                quantity,
                plannedStopLossPrice,
                investmentReason
        );
    }
}
