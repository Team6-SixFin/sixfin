package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsCommandRepository;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import com.sparta.trading.infrastructure.monitoring.TradingMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@AllArgsConstructor
public class TradingKafkaOutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final OutboxEventsQueryRepository outboxEventsQueryRepository;
    private final TradingKafkaProducer producer;
    private final TradingKafkaOutboxMarker marker;
    private final OutboxPublisherProperties outboxPublisherProperties;
    private final TradingMetrics tradingMetrics;

    /**
     * 카프카 전송 결과(성공/실패) 기록은 {@link TradingKafkaOutboxMarker}가 별도 트랜잭션으로 처리한다.
     * 전송 자체는 트랜잭션 밖에서 이뤄져 DB 커넥션을 카프카 응답 대기 동안 붙잡지 않는다.
     */
    public boolean publishOne(Long id){
        OutboxEvents outboxEvents = outboxEventsQueryRepository.findById(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));

        // 멱등 처리이므로 성공
        if(!OutboxStatus.PENDING.equals(outboxEvents.getStatus())) return true;

        Timer.Sample sendSample = tradingMetrics.startTimer();
        try {
            producer.sendSync(
                    outboxPublisherProperties.topic(),
                    outboxEvents.getPartitionKey(),
                    outboxEvents.getPayload(),
                    SEND_TIMEOUT_SECONDS
            );
        } catch (Exception e) {
            log.error("[Outbox Publisher] Failed to publish outboxEventId={}", id, e);
            marker.markFailedAttempt(e, outboxPublisherProperties.maxRetry(), id);
            tradingMetrics.recordPublishFailure(sendSample);
            return false;
        }
        marker.markPublished(id);
        tradingMetrics.recordPublishSuccess(sendSample, outboxEvents.getOccurredAt());
        return true;
    }
}
