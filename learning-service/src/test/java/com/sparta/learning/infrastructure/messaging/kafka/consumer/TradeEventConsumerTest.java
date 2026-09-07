package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.facade.TradeEventFacade;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka Listener가 message key를 검증하고 Facade에 이벤트를 전달하는지 검증
 */
@ExtendWith(MockitoExtension.class)
class TradeEventConsumerTest {

    @Mock
    private TradeEventFacade tradeEventFacade;

    private ObjectMapper objectMapper;
    private TradeEventConsumer consumer;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new TradeEventConsumer(tradeEventFacade, new LearningMetrics(meterRegistry));
    }

    // userId와 같은 message key가 오면 이벤트를 Facade로 전달하는지 확인
    @Test
    void delegatesValidRecordToFacade() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ConsumerRecord<String, TradingEventEnvelope> record = new ConsumerRecord<>(
                "trade-events.v1",
                0,
                10L,
                event.userId().toString(),
                event
        );
        when(tradeEventFacade.handle(event))
                .thenReturn(IngestionResult.processed(mock(ExecutionSnapshot.class)));

        consumer.consume(record);

        verify(tradeEventFacade).handle(event);
        assertThat(meterRegistry.get("learning.trade.events")
                .tag("event_type", "BUY_EXECUTED")
                .tag("result", "PROCESSED")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("learning.trade.event.processing.duration")
                .tag("event_type", "BUY_EXECUTED")
                .tag("result", "PROCESSED")
                .timer()
                .count()).isEqualTo(1L);
    }

    // 중복 이벤트는 실패가 아니라 정상적인 멱등 처리 결과로 별도 집계하는지 확인
    @Test
    void recordsDuplicateEventSeparately() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ConsumerRecord<String, TradingEventEnvelope> record = new ConsumerRecord<>(
                "trade-events.v1",
                0,
                11L,
                event.userId().toString(),
                event
        );
        when(tradeEventFacade.handle(event)).thenReturn(IngestionResult.duplicate());

        consumer.consume(record);

        assertThat(meterRegistry.get("learning.trade.events")
                .tag("event_type", "BUY_EXECUTED")
                .tag("result", "DUPLICATE")
                .counter()
                .count()).isEqualTo(1.0);
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

        verify(tradeEventFacade, never()).handle(event);
        assertThat(meterRegistry.get("learning.trade.events")
                .tag("event_type", "BUY_EXECUTED")
                .tag("result", "FAILED")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
