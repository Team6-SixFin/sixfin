package com.sparta.learning.infrastructure.messaging.kafka.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.domain.model.TradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TradingEventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Test
    void firstBuySampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        BuyExecutedPayload payload = objectMapper.treeToValue(event.payload(), BuyExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.BUY_EXECUTED);
        assertThat(payload.newPosition()).isTrue();
        assertThat(payload.stockCode()).isEqualTo("AAPL");
        assertThat(payload.marketContext().recent20DayHigh()).isNotNull();
    }

    @Test
    void additionalBuySampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-additional.json");
        BuyExecutedPayload payload = objectMapper.treeToValue(event.payload(), BuyExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.BUY_EXECUTED);
        assertThat(payload.newPosition()).isFalse();
        assertThat(payload.positionQuantityAfter()).isEqualTo(15);
    }

    @Test
    void sellSampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/sell-executed.json");
        SellExecutedPayload payload = objectMapper.treeToValue(event.payload(), SellExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.SELL_EXECUTED);
        assertThat(payload.stockCode()).isEqualTo("AAPL");
        assertThat(payload.positionQuantityAfter()).isEqualTo(10);
    }

    @Test
    void positionClosedSampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/position-closed.json");
        PositionClosedPayload payload = objectMapper.treeToValue(event.payload(), PositionClosedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.POSITION_CLOSED);
        assertThat(payload.totalQuantity()).isEqualTo(15);
        assertThat(payload.stockCode()).isEqualTo("AAPL");
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
