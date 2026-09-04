package com.sparta.learning.application.model;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ExecutionSnapshot;

/* 이벤트 수집 결과와 후속 처리에 필요한 스냅샷을 같이 전달한다.
 * 체결과 포지션 종료는 진단 대상 타입이 달라 각각 담는다.
 * 중복 이벤트는 이미 진단이 저장되어 있으므로 어느 쪽도 담지 않는다. */
public record IngestionResult(
        EventIngestionResult status,
        ExecutionSnapshot executionSnapshot,
        ClosedPositionSnapshot closedPositionSnapshot
) {

    public static IngestionResult duplicate(){
        return new IngestionResult(EventIngestionResult.DUPLICATE, null, null);
    }

    public static IngestionResult processed(ExecutionSnapshot executionSnapshot){
        return new IngestionResult(EventIngestionResult.PROCESSED, executionSnapshot, null);
    }

    public static IngestionResult processed(ClosedPositionSnapshot closedPositionSnapshot){
        return new IngestionResult(EventIngestionResult.PROCESSED, null, closedPositionSnapshot);
    }

    // 체결 진단(ENTRY, TRADE) 대상인지
    public boolean hasExecutionTarget(){
        return executionSnapshot != null;
    }

    // 포지션 종료 진단(CLOSE) 대상인지
    public boolean hasClosedPositionTarget(){
        return closedPositionSnapshot != null;
    }
}
