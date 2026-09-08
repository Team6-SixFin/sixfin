package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outbox.OutboxEventRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@AllArgsConstructor
public class TradingKafkaOutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final OutboxEventRepository outboxEventsRepository;
    private final TradingKafkaProducer producer;
    private final OutboxPublisherProperties outboxPublisherProperties;

    /**
     * 이벤트 1건 = 독립 트랜잭션. 배치 전체를 하나로 묶으면 한 건 실패가 이미 발행된 다른 건의
     * 상태 갱신까지 롤백시켜 중복 발행을 유발하므로, 스케줄러가 아니라 이 메서드 단위로 커밋한다.
     */
    @Transactional
    public void publishOne(Long id){
        OutboxEvents outboxEvents = outboxEventsRepository.findById(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));

        if(!OutboxStatus.PENDING.equals(outboxEvents.getStatus())) return;

        try {
            producer.sendSync(
                    outboxPublisherProperties.topic(),
                    outboxEvents.getPartitionKey(),
                    outboxEvents.getPayload(),
                    SEND_TIMEOUT_SECONDS
            );
            outboxEvents.markPublished(Instant.now());
        } catch (Exception e) {
            log.error("[Outbox Publisher] Failed to publish outboxEventId={}", id, e);
            outboxEvents.markFailedAttempt(e.getMessage(), outboxPublisherProperties.maxRetry());
        }
    }
}
