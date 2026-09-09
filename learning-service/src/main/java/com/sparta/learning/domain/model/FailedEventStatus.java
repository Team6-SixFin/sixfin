package com.sparta.learning.domain.model;

// DLT로 보관된 이벤트의 재처리 상태 (운영자가 원인을 확인한 뒤 직접 재처리함)
public enum FailedEventStatus {

    // 재처리 대기
    PENDING,
    // 재처리 진행 중. 같은 이벤트를 동시에 두 번 재처리하지 않도록 선점한 상태
    PROCESSING,
    // 재처리로 정상 반영됨
    RESOLVED
}
