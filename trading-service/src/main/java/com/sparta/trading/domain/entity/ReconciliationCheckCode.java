package com.sparta.trading.domain.entity;

/** 정합성 대사 검사 항목. 선언 순서 = 구현 우선순위. */
public enum ReconciliationCheckCode {
    NEGATIVE_CASH("예수금 음수"),
    NEGATIVE_QUANTITY("포지션 수량 음수"),
    DUPLICATE_REQUEST("중복 request_id"),
    DUPLICATE_OPEN_POSITION("OPEN 포지션 중복"),
    OUTBOX_PENDING("미발행 Outbox 적체"),
    LEDGER_BALANCE("예수금 = 원장 합계"),
    POSITION_QUANTITY("포지션 수량 = 체결 합계"),
    LEDGER_SEQUENCE("원장 잔액 연속성");

    private final String description;

    ReconciliationCheckCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
