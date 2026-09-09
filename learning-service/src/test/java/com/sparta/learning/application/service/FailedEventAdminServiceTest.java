package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.application.dto.response.FailedEventRetryResponse;
import com.sparta.learning.application.facade.TradeEventFacade;
import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.domain.model.TradeEventType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.persistence.repository.FailedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 실패 이벤트 재처리 흐름과 상태 갱신을 검증합니다
@ExtendWith(MockitoExtension.class)
class FailedEventAdminServiceTest {

    private static final Long FAILED_EVENT_ID = 1024L;

    @Mock
    private FailedEventRepository failedEventRepository;

    @Mock
    private FailedEventStatusUpdater statusUpdater;

    @Mock
    private TradeEventFacade tradeEventFacade;

    private ObjectMapper objectMapper;
    private FailedEventAdminService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        service = new FailedEventAdminService(
                failedEventRepository, statusUpdater, tradeEventFacade, objectMapper);
    }

    // 보관된 원본으로 수집부터 다시 실행해야 수집 실패와 진단 실패를 둘다 복구할 수 있다
    @Test
    void 재처리는_보관된_원본으로_파이프라인을_다시_실행한다() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        when(statusUpdater.loadPending(FAILED_EVENT_ID)).thenReturn(failedEvent(event));
        when(statusUpdater.markResolved(FAILED_EVENT_ID)).thenReturn(resolvedFailedEvent(event));

        FailedEventRetryResponse response = service.retry(FAILED_EVENT_ID);

        verify(tradeEventFacade).handle(any(TradingEventEnvelope.class));
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.retryCount()).isEqualTo(1);
    }

    @Test
    void 재처리가_실패하면_상태를_해결로_바꾸지_않는다() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        when(statusUpdater.loadPending(FAILED_EVENT_ID)).thenReturn(failedEvent(event));
        doThrow(new IllegalStateException("진단 실패"))
                .when(tradeEventFacade).handle(any(TradingEventEnvelope.class));

        assertThatThrownBy(() -> service.retry(FAILED_EVENT_ID))
                .isInstanceOf(CustomException.class);

        verify(statusUpdater, never()).markResolved(any());
    }

    @Test
    void 재처리가_실패하면_실패_사유를_갱신한다() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        when(statusUpdater.loadPending(FAILED_EVENT_ID)).thenReturn(failedEvent(event));
        doThrow(new IllegalStateException("진단 실패"))
                .when(tradeEventFacade).handle(any(TradingEventEnvelope.class));

        assertThatThrownBy(() -> service.retry(FAILED_EVENT_ID))
                .isInstanceOf(CustomException.class);

        verify(statusUpdater).markRetryFailed(eq(FAILED_EVENT_ID),
                org.mockito.ArgumentMatchers.contains("진단 실패"));
    }

    @Test
    void 이미_재처리된_이벤트는_다시_실행하지_않는다() {
        when(statusUpdater.loadPending(FAILED_EVENT_ID))
                .thenThrow(new CustomException(LearningErrorCode.FAILED_EVENT_ALREADY_RESOLVED));

        assertThatThrownBy(() -> service.retry(FAILED_EVENT_ID))
                .isInstanceOf(CustomException.class);

        verify(tradeEventFacade, never()).handle(any());
    }

    // 저장 시점과 재처리 시점 사이에 이벤트 구조가 바뀌면 복원에 실패할 수 있다
    // 재처리를 시도하다 실패한 것과 구분되도록 별도 코드를 사용한다
    @Test
    void payload를_복원할_수_없으면_재처리하지_않는다() {
        FailedEvent broken = FailedEvent.builder()
                .eventId(java.util.UUID.randomUUID())
                .eventType(TradeEventType.BUY_EXECUTED)
                .payload(objectMapper.createObjectNode().put("eventId", "not-a-uuid"))
                .build();
        when(statusUpdater.loadPending(FAILED_EVENT_ID)).thenReturn(broken);

        assertThatThrownBy(() -> service.retry(FAILED_EVENT_ID))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", LearningErrorCode.FAILED_EVENT_PAYLOAD_BROKEN);

        verify(tradeEventFacade, never()).handle(any());
    }

    private FailedEvent failedEvent(TradingEventEnvelope event) {
        return FailedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .userId(event.userId())
                .payload(objectMapper.valueToTree(event))
                .failureReason("java.lang.IllegalStateException: 손절가 계산 실패")
                .originalTopic("trade-events.v1")
                .originalPartition(2)
                .originalOffset(1523L)
                .build();
    }

    private FailedEvent resolvedFailedEvent(TradingEventEnvelope event) {
        FailedEvent failedEvent = failedEvent(event);
        failedEvent.resolve();
        return failedEvent;
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
