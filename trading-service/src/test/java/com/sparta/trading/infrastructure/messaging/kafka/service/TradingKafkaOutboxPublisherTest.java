package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import com.sparta.trading.infrastructure.monitoring.TradingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * publishOne이 카프카 전송 결과에 따라 {@link TradingKafkaOutboxMarker}에 올바르게 위임하는지 검증한다.
 * 실제 상태 전이(PENDING→PUBLISHED/FAILED) 로직은 {@link TradingKafkaOutboxMarkerTest}에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TradingKafkaOutboxPublisherTest {

    private static final Long OUTBOX_ID = 1L;
    private static final String TOPIC = "trade-events.v1";
    private static final int MAX_RETRY = 5;

    @Mock
    private OutboxEventsQueryRepository outboxEventsRepository;

    @Mock
    private TradingKafkaProducer producer;

    @Mock
    private TradingKafkaOutboxMarker marker;

    private OutboxEvents pendingEvent;
    private TradingMetrics tradingMetrics;

    @BeforeEach
    void setUp() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("stub", true);
        pendingEvent = OutboxEvents.buyExecuted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), payload, Instant.now());
        tradingMetrics = new TradingMetrics(new SimpleMeterRegistry(), outboxEventsRepository);
    }

    private TradingKafkaOutboxPublisher publisher() {
        return new TradingKafkaOutboxPublisher(
                outboxEventsRepository, producer, marker,
                new OutboxPublisherProperties(TOPIC, 100, MAX_RETRY, 4),
                tradingMetrics);
    }

    @Test
    void throwsWhenOutboxEventNotFound() {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisher().publishOne(OUTBOX_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND);

        verifyNoInteractions(marker);
    }

    @Test
    void skipsSendWhenStatusIsNotPending() throws Exception {
        pendingEvent.markPublished(Instant.now());
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        boolean result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isTrue();
        verify(producer, never()).sendSync(anyString(), anyString(), any(), anyLong());
        verifyNoInteractions(marker);
    }

    @Test
    void delegatesToMarkerMarkPublishedOnSuccessfulSend() throws Exception {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any(), anyLong()))
                .thenReturn(mock(SendResult.class));

        boolean result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isTrue();
        verify(marker).markPublished(OUTBOX_ID);
        verify(marker, never()).markFailedAttempt(any(), anyInt(), anyLong());
    }

    @Test
    void delegatesToMarkerMarkFailedAttemptOnSendFailure() throws Exception {
        RuntimeException sendFailure = new RuntimeException("broker unavailable");
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any(), anyLong()))
                .thenThrow(sendFailure);

        boolean result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isFalse();
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(marker).markFailedAttempt(exceptionCaptor.capture(), eq(MAX_RETRY), eq(OUTBOX_ID));
        assertThat(exceptionCaptor.getValue()).isSameAs(sendFailure);
        verify(marker, never()).markPublished(anyLong());
    }
}
