package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/** 사용자가 입력한 주문 정보를 확인하고 정리해서 실제 주문 처리에 사용할 주문 정보로 만듬 */
record NormalizedOrder(
        UUID requestId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        int quantity,
        BigDecimal plannedStopLossPrice,
        String investmentReason
) {

    static NormalizedOrder from(PlaceOrderCommand command) {
        // symbol 비교와 시세 조회가 안정적으로 동작하도록 공백을 제거하고 대문자화한다.
        String symbol = command.symbol().trim().toUpperCase(Locale.ROOT);
        boolean isBuy = command.side() == OrderSide.BUY;

        // 매수 주문에 손절가가 있으면 금액 형식을 맞추고, 매도이거나 손절가가 없으면 null로 처리
        BigDecimal stopLoss = !isBuy || command.plannedStopLossPrice() == null
                ? null
                : money(command.plannedStopLossPrice());

        // 매수 주문에 투자 이유가 있으면 앞뒤 공백을 제거하고, 매도이거나 투자 이유가 없으면 null로 처리
        String investmentReason = !isBuy
                || command.investmentReason() == null
                || command.investmentReason().isBlank()
                ? null
                : command.investmentReason().trim();

        return new NormalizedOrder(
                command.requestId(),
                symbol,
                command.side(),
                command.orderType(),
                command.quantity(),
                stopLoss,
                investmentReason
        );
    }

    /** 금액을 소수점 넷째 자리까지 반올림 */
    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
}
