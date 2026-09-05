package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.facade.TradeEventFacade;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * trade-events.v1을 수신하는 진입점
 * 예외를 여기서 삼키지 않아 저장 실패 시 offset이 커밋되지 않고 Kafka 재시도가 동작하게 함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventConsumer {

    // 수집과 진단의 실행순서는 Facade가 결정함
    private final TradeEventFacade tradeEventFacade;
    private final LearningMetrics learningMetrics;

    @KafkaListener(topics = "${learning.kafka.topics.trade-events}")
    public void consume(ConsumerRecord<String, TradingEventEnvelope> record) {
        TradingEventEnvelope event = record.value();
        Timer.Sample sample = learningMetrics.startTimer();

        try {
            validateMessageKey(record.key(), event);

            IngestionResult result = tradeEventFacade.handle(event);
            learningMetrics.recordTradeEvent(event.eventType(), result.status(), sample);
            log.info(
                    "Trade event ingestion completed. eventId={}, eventType={}, result={}, partition={}, offset={}",
                    event.eventId(),
                    event.eventType(),
                    result.status(),
                    record.partition(),
                    record.offset()
            );
        } catch (RuntimeException exception) {
            learningMetrics.recordTradeEventFailure(event == null ? null : event.eventType(), sample);
            throw exception;
        }
    }

    private void validateMessageKey(String messageKey, TradingEventEnvelope event) {
        if (event == null) {
            throw new InvalidTradeEventException("Kafka 이벤트 본문이 없습니다.");
        }

        if (event.userId() == null) {
            throw new InvalidTradeEventException("Kafka 이벤트에 userId가 없습니다. eventId=" + event.eventId());
        }

        if (messageKey == null || !messageKey.equals(event.userId().toString())) {
            throw new InvalidTradeEventException(
                    "Kafka message key와 userId가 일치하지 않습니다. eventId=" + event.eventId()
            );
        }
    }
}
