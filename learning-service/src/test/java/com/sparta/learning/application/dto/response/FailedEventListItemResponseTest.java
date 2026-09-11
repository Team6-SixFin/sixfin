package com.sparta.learning.application.dto.response;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.domain.model.TradeEventType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 본문을 해석하지 못해 식별값이 비어 있는 이벤트도 목록에 담기는지 검증합니다
class FailedEventListItemResponseTest {

    @Test
    void 해석한_이벤트는_종류를_문자열로_변환한다() {
        FailedEvent failedEvent = FailedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(TradeEventType.BUY_EXECUTED)
                .payload(JsonMapper.builder().build().createObjectNode())
                .build();

        FailedEventListItemResponse response = FailedEventListItemResponse.from(failedEvent);

        assertThat(response.eventType()).isEqualTo("BUY_EXECUTED");
    }

    // eventType이 null인 행 하나 때문에 목록 조회 전체가 실패하면 안 된다
    @Test
    void 해석하지_못한_이벤트도_목록에_담긴다() {
        FailedEvent failedEvent = FailedEvent.builder()
                .payload(JsonMapper.builder().build().createObjectNode().put("_unparsed", true))
                .originalTopic("trade-events.v1")
                .originalOffset(1523L)
                .build();

        FailedEventListItemResponse response = FailedEventListItemResponse.from(failedEvent);

        assertThat(response.eventId()).isNull();
        assertThat(response.eventType()).isNull();
        assertThat(response.originalTopic()).isEqualTo("trade-events.v1");
        assertThat(response.status()).isEqualTo("PENDING");
    }
}
