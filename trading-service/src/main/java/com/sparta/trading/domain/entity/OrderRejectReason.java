package com.sparta.trading.domain.entity;

public enum OrderRejectReason {
    INSUFFICIENT_CASH, // 금액 부족
    INSUFFICIENT_POSITION_QUANTITY, // 포지션 수량 부족
    MARKET_CONTEXT_UNAVAILABLE // 시장 상황 정보를 이용할 수 없음
}
