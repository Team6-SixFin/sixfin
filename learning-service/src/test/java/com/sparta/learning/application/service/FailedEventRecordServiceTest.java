package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.persistence.repository.FailedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// 본문을 해석하지 못한 DLT 메시지도 유실되지 않고 보관되는지 검증합니다
@ExtendWith(MockitoExtension.class)
class FailedEventRecordServiceTest {

    @Mock
    private FailedEventRepository failedEventRepository;

    private ObjectMapper objectMapper;
    private FailedEventRecordService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        service = new FailedEventRecordService(failedEventRepository, objectMapper);
    }

    @Test
    void 본문을_해석한_이벤트는_식별값과_함께_보관한다() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");

        service.record(event, null, "trade-events.v1", 2, 1523L, "IllegalStateException: 손절가 계산 실패");

        FailedEvent saved = captureSaved();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getEventType()).isEqualTo(event.eventType());
        assertThat(saved.getPayload().path("eventId").asText()).isEqualTo(event.eventId().toString());
    }

    // 보관하지 않으면 DLT offset이 커밋되어 실패 메시지가 유실된다
    @Test
    void 본문을_해석하지_못해도_원본을_보관한다() {
        service.record(null, "{\"eventId\":\"broken\"", "trade-events.v1", 2, 1523L, "JsonParseException");

        FailedEvent saved = captureSaved();
        assertThat(saved.getEventId()).isNull();
        assertThat(saved.getEventType()).isNull();
        assertThat(saved.getOriginalTopic()).isEqualTo("trade-events.v1");
        assertThat(saved.getOriginalOffset()).isEqualTo(1523L);
        assertThat(saved.getPayload().path("_unparsed").asBoolean()).isTrue();
        assertThat(saved.getPayload().path("_raw").asText()).isEqualTo("{\"eventId\":\"broken\"");
    }

    @Test
    void 원본_바이트조차_없으면_해석_실패만_남긴다() {
        service.record(null, null, "trade-events.v1", 2, 1523L, "JsonParseException");

        FailedEvent saved = captureSaved();
        assertThat(saved.getEventId()).isNull();
        assertThat(saved.getPayload().path("_unparsed").asBoolean()).isTrue();
        assertThat(saved.getPayload().path("_raw").isNull()).isTrue();
    }

    private FailedEvent captureSaved() {
        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
