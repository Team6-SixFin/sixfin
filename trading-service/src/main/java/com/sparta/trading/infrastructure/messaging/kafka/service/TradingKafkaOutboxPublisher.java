package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import com.sparta.trading.infrastructure.monitoring.TradingMetrics;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@AllArgsConstructor
public class TradingKafkaOutboxPublisher {

    private final OutboxEventsQueryRepository outboxEventsQueryRepository;
    private final TradingKafkaProducer producer;
    private final TradingKafkaOutboxMarker marker;
    private final OutboxPublisherProperties outboxPublisherProperties;
    private final TradingMetrics tradingMetrics;

    /**
     * 카프카 전송 결과(성공/실패) 기록은 {@link TradingKafkaOutboxMarker}가 별도 트랜잭션으로 처리한다.
     * 전송 자체는 트랜잭션 밖에서 이뤄져 DB 커넥션을 카프카 응답 대기 동안 붙잡지 않는다.
     */
    public OutboxPublishResult publishOne(Long id){
        OutboxEvents outboxEvents = outboxEventsQueryRepository.findById(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));

        // 이미 발행된 건만 스킵한다. PENDING은 정상 흐름, FAILED는 관리자 재발행(publishOne 재호출)을 허용하기 위함.
        if(OutboxStatus.PUBLISHED.equals(outboxEvents.getStatus())) return OutboxPublishResult.PUBLISHED;

        Timer.Sample sendSample = tradingMetrics.startTimer();
        try {
            producer.sendSync(
                    outboxPublisherProperties.topic(),
                    outboxEvents.getPartitionKey(),
                    outboxEvents.getPayload()
            );
        } catch (Exception e) {
            tradingMetrics.recordPublishFailure(sendSample);
            if (isTransient(e)) {
                log.warn("[Outbox Publisher] Temporary Kafka failure, outboxEventId={} remains PENDING", id, e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return OutboxPublishResult.RETRY_LATER;
            }
            int maxRetry = isPermanent(e) ? 1 : outboxPublisherProperties.maxRetry();
            OutboxStatus status = marker.markFailedAttempt(e, maxRetry, id);
            log.error("[Outbox Publisher] Failed to publish outboxEventId={}, status={}", id, status, e);
            return status == OutboxStatus.FAILED ? OutboxPublishResult.FAILED : OutboxPublishResult.RETRY_WITHOUT_PAUSE;
        }
        marker.markPublished(id);
        tradingMetrics.recordPublishSuccess(sendSample, outboxEvents.getOccurredAt());
        return OutboxPublishResult.PUBLISHED;
    }

    private boolean isTransient(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RetriableException || cause instanceof TimeoutException
                    || cause instanceof InterruptedException) return true;
        }
        return false;
    }

    private boolean isPermanent(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SerializationException || cause instanceof RecordTooLargeException
                    || cause instanceof InvalidTopicException) return true;
        }
        return false;
    }
}
