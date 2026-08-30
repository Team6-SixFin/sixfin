package com.sparta.learning.infrastructure.messaging.kafka.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.domain.model.TradeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trading이 발행하는 샘플 JSON을 Learning의 이벤트 DTO로 변환할 수 있는지 검증한다.
 * 이 테스트가 실패하면 두 서비스가 합의한 Kafka 필드명이나 데이터 타입이 달라진 것이다.
 */
class TradingEventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    // 최초 매수 이벤트가 BUY Payload로 변환되고 신규 포지션 및 시장 맥락이 유지되는지 확인한다.
    @Test
    void firstBuySampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        BuyExecutedPayload payload = objectMapper.treeToValue(event.payload(), BuyExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.BUY_EXECUTED);
        assertThat(payload.newPosition()).isTrue();
        assertThat(payload.stockCode()).isEqualTo("AAPL");
        assertThat(payload.marketContext().recent20DayHigh()).isNotNull();
    }

    // 추가 매수 이벤트에서 신규 포지션 여부가 false이고 체결 후 보유수량이 전달되는지 확인한다.
    @Test
    void additionalBuySampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-additional.json");
        BuyExecutedPayload payload = objectMapper.treeToValue(event.payload(), BuyExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.BUY_EXECUTED);
        assertThat(payload.newPosition()).isFalse();
        assertThat(payload.positionQuantityAfter()).isEqualTo(15);
    }

    // 부분 매도 이벤트가 SELL Payload로 변환되고 매도 후 잔여수량이 전달되는지 확인한다.
    @Test
    void sellSampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/sell-executed.json");
        SellExecutedPayload payload = objectMapper.treeToValue(event.payload(), SellExecutedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.SELL_EXECUTED);
        assertThat(payload.stockCode()).isEqualTo("AAPL");
        assertThat(payload.positionQuantityAfter()).isEqualTo(10);
    }

    // 전량 매도 후 POSITION_CLOSED 이벤트에 최종 수량과 미국 주식 티커가 포함되는지 확인한다.
    @Test
    void positionClosedSampleCanBeDeserialized() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/position-closed.json");
        PositionClosedPayload payload = objectMapper.treeToValue(event.payload(), PositionClosedPayload.class);

        assertThat(event.eventType()).isEqualTo(TradeEventType.POSITION_CLOSED);
        assertThat(payload.totalQuantity()).isEqualTo(15);
        assertThat(payload.stockCode()).isEqualTo("AAPL");
    }

    // 공통 Envelope를 먼저 변환한 뒤 각 이벤트 타입에 맞는 Payload 변환 테스트에서 재사용한다.
    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
