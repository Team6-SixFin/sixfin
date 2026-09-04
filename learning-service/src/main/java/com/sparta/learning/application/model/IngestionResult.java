package com.sparta.learning.application.model;

import com.sparta.learning.domain.entity.ExecutionSnapshot;

/* 이벤트 수집 겨로가와 후속 처리에 필요한 스냅샷을 같이 전달한다. */
public record IngestionResult(
        EventIngestionResult status,
        ExecutionSnapshot executionSnapshot
) {

    public static IngestionResult duplicate(){
        return new IngestionResult(EventIngestionResult.DUPLICATE, null);
    }

    public static IngestionResult processed(ExecutionSnapshot executionSnapshot){
        return new IngestionResult(EventIngestionResult.PROCESSED, executionSnapshot);
    }

    // 포지션 종료는 ClosedPositionSnapshot이라 현재 진단 인터페이스로 처리할 수 없다 -> CLOSE 규칙 구현시 정리
    public static IngestionResult processedWithoutDiagnosis(){
        return new IngestionResult(EventIngestionResult.PROCESSED, null);
    }

    public boolean hasDiagnosisTarget(){
        return executionSnapshot != null;
    }
}
