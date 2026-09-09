package com.sparta.trading.infrastructure.messaging.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox Publisher 스케줄러의 토픽/배치 크기/최대 재시도 횟수를 코드가 아닌 yml로 관리
 */
@ConfigurationProperties(prefix = "trading.outbox.publisher")
public record OutboxPublisherProperties(
        String topic,
        int batchSize,
        int maxRetry
) {
}
