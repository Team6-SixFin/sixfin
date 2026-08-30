package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
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

    private final TradeEventIngestionService ingestionService;

    @KafkaListener(topics = "${learning.kafka.topics.trade-events}")
    public void consume(ConsumerRecord<String, TradingEventEnvelope> record) {
        TradingEventEnvelope event = record.value();
        validateMessageKey(record.key(), event);

        EventIngestionResult result = ingestionService.ingest(event);
        log.info(
                "Trade event ingestion completed. eventId={}, eventType={}, result={}, partition={}, offset={}",
                event.eventId(),
                event.eventType(),
                result,
                record.partition(),
                record.offset()
        );
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
