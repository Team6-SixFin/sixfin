package com.sparta.learning.domain.model;

import com.sparta.learning.domain.entity.ExecutionSnapshot;

public enum DiagnosisPhase {
    ENTRY,
    TRADE,
    CLOSE;

    // 체결 스냅샷이 어느 진단 단계인지 판단
    // isNewPosition로 ENTRY인지 TRADE인지 판별
    // CLOSE는 ClosedPositionSnapshot으로 따로 들어옴
    public static DiagnosisPhase from(ExecutionSnapshot snapshot) {
        return snapshot.getTradeType() == TradeType.BUY && snapshot.isNewPosition()
                ? ENTRY : TRADE;
    }
}
