package com.sparta.learning.infrastructure.messaging.kafka.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ConsumedEvent;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trading과 합의한 샘플 이벤트의 값이 Learning 스냅샷에 빠짐없이 매핑되는지 검증
 */
class TradeEventSnapshotMapperTest {

    private ObjectMapper objectMapper;
    private ValidatorFactory validatorFactory;
    private TradeEventSnapshotMapper mapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        mapper = new TradeEventSnapshotMapper(objectMapper, validatorFactory.getValidator());
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    // 최초 매수의 계획 정보와 시장 맥락이 BUY 체결 스냅샷에 보존되는지 확인
    @Test
    void mapsFirstBuyEventToExecutionSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ConsumedEvent consumedEvent = createConsumedEvent(event);

        ExecutionSnapshot snapshot = mapper.toBuySnapshot(consumedEvent, event);

        assertThat(snapshot.getConsumedEvent()).isSameAs(consumedEvent);
        assertThat(snapshot.getUserId()).isEqualTo(event.userId());
        assertThat(snapshot.getStockSymbol()).isEqualTo("AAPL");
        assertThat(snapshot.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(snapshot.isNewPosition()).isTrue();
        assertThat(snapshot.getPlannedStopLossPrice()).isEqualByComparingTo("174.0000");
        assertThat(snapshot.getRecent20dHigh()).isEqualByComparingTo("187.4000");
        assertThat(snapshot.getRecent20dLow()).isEqualByComparingTo("169.2100");
        assertThat(snapshot.getRecent5dReturnRate()).isEqualByComparingTo("5.2000");
    }

    // 매도 체결의 실현손익과 체결 후 잔여수량이 SELL 체결 스냅샷에 저장되는지 확인
    @Test
    void mapsSellEventToExecutionSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/sell-executed.json");
        ConsumedEvent consumedEvent = createConsumedEvent(event);

        ExecutionSnapshot snapshot = mapper.toSellSnapshot(consumedEvent, event);

        assertThat(snapshot.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(snapshot.isNewPosition()).isFalse();
        assertThat(snapshot.getPositionQuantityAfter()).isEqualTo(10);
        assertThat(snapshot.getExecutionRealizedProfit()).isEqualByComparingTo("-14.7335");
        assertThat(snapshot.getRecent20dHigh()).isNull();
    }

    // 전량 매도된 포지션의 최종 집계가 종료 포지션 스냅샷으로 변환되는지 확인
    @Test
    void mapsPositionClosedEventToClosedPositionSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/position-closed.json");
        ConsumedEvent consumedEvent = createConsumedEvent(event);

        ClosedPositionSnapshot snapshot = mapper.toClosedPositionSnapshot(consumedEvent, event);

        assertThat(snapshot.getConsumedEvent()).isSameAs(consumedEvent);
        assertThat(snapshot.getPositionId()).isNotNull();
        assertThat(snapshot.getTotalBoughtQuantity()).isEqualTo(15);
        assertThat(snapshot.getTotalSoldQuantity()).isEqualTo(15);
        assertThat(snapshot.getAverageEntryPrice()).isEqualByComparingTo(new BigDecimal("182.4467"));
        assertThat(snapshot.getRealizedReturnRate()).isEqualByComparingTo("-2.9851");
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }

    private ConsumedEvent createConsumedEvent(TradingEventEnvelope event) {
        return ConsumedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .eventVersion(event.eventVersion())
                .userId(event.userId())
                .payload(event.payload())
                .occurredAt(event.occurredAt())
                .build();
    }
}
