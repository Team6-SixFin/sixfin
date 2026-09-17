package com.sparta.trading.infrastructure.messaging.kafka.service;

public enum OutboxPublishResult {
    PUBLISHED,
    RETRY_LATER,
    RETRY_WITHOUT_PAUSE,
    FAILED
}
