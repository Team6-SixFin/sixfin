package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka Listener가 message key를 검증하고 수집 서비스에 이벤트를 전달하는지 검증
 */
@ExtendWith(MockitoExtension.class)
class TradeEventConsumerTest {

    @Mock
    private TradeEventIngestionService ingestionService;

    private ObjectMapper objectMapper;
    private TradeEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        consumer = new TradeEventConsumer(ingestionService);
    }

    // userId와 같은 message key가 오면 이벤트를 수집 서비스로 전달하는지 확인
    @Test
    void delegatesValidRecordToIngestionService() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ConsumerRecord<String, TradingEventEnvelope> record = new ConsumerRecord<>(
                "trade-events.v1",
                0,
                10L,
                event.userId().toString(),
                event
        );
        when(ingestionService.ingest(event)).thenReturn(EventIngestionResult.PROCESSED);

        consumer.consume(record);

        verify(ingestionService).ingest(event);
    }

    // 다른 userId를 key로 사용한 이벤트는 사용자별 순서 보장을 깨므로 저장 전에 거부하는지 확인
    @Test
    void rejectsRecordWhenMessageKeyDoesNotMatchUserId() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ConsumerRecord<String, TradingEventEnvelope> record = new ConsumerRecord<>(
                "trade-events.v1",
                0,
                10L,
                "different-user-id",
                event
        );

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(InvalidTradeEventException.class)
                .hasMessageContaining("message key");

        verify(ingestionService, never()).ingest(event);
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
