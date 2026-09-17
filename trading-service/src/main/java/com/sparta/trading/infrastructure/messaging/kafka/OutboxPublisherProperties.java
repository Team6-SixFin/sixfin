package com.sparta.trading.infrastructure.messaging.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox Publisher의 토픽, 배치 크기, 재시도 횟수, 병렬도, 일시 장애 후 대기 시간을 관리한다.
 */
@ConfigurationProperties(prefix = "trading.outbox.publisher")
public record OutboxPublisherProperties(
        String topic,
        int batchSize,
        int maxRetry,
        int parallelism,
        long retryPauseMs
) {
}
