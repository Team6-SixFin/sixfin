package com.sparta.learning.application.model;

/**
 * Kafka 이벤트 수집 결과입니다.
 * 중복 이벤트는 정상 처리로 간주해 Consumer가 같은 이벤트를 계속 재시도하지 않게 합니다.
 */
public enum EventIngestionResult {
    PROCESSED,
    DUPLICATE
}
