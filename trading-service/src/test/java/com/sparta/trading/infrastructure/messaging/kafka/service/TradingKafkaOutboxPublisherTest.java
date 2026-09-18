package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import com.sparta.trading.infrastructure.monitoring.TradingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.errors.RecordTooLargeException;
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
import java.util.concurrent.ExecutionException;

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
                new OutboxPublisherProperties(TOPIC, 100, MAX_RETRY, 4, 5000),
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

        OutboxPublishResult result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxPublishResult.PUBLISHED);
        verify(producer, never()).sendSync(anyString(), anyString(), any());
        verifyNoInteractions(marker);
    }

    @Test
    void schedulerSkipsFailedEvents() throws Exception {
        pendingEvent.markFailedAttempt("previous failure", 1); // FAILED로 전이
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        OutboxPublishResult result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxPublishResult.PUBLISHED);
        verifyNoInteractions(producer, marker);
    }

    @Test
    void manualRetryClaimsFailedEventBeforeSend() throws Exception {
        pendingEvent.markFailedAttempt("previous failure", 1);
        pendingEvent.setStatus(OutboxStatus.RETRYING);
        when(marker.claimFailedForRetry(OUTBOX_ID, null)).thenReturn(pendingEvent);
        when(producer.sendSync(eq(TOPIC), anyString(), any())).thenReturn(mock(SendResult.class));

        assertThat(publisher().republishOne(OUTBOX_ID, null)).isEqualTo(OutboxPublishResult.PUBLISHED);
        verify(marker).markPublished(OUTBOX_ID);
    }

    @Test
    void manualTransientFailureReleasesClaimWithoutCountingRetry() throws Exception {
        pendingEvent.markFailedAttempt("previous failure", 1);
        pendingEvent.setStatus(OutboxStatus.RETRYING);
        Exception failure = new ExecutionException(new org.apache.kafka.common.errors.TimeoutException("broker unavailable"));
        when(marker.claimFailedForRetry(OUTBOX_ID, null)).thenReturn(pendingEvent);
        when(producer.sendSync(eq(TOPIC), anyString(), any())).thenThrow(failure);

        assertThat(publisher().republishOne(OUTBOX_ID, null)).isEqualTo(OutboxPublishResult.RETRY_LATER);
        verify(marker).markManualRetryFailed(OUTBOX_ID, failure, false);
    }

    @Test
    void manualPermanentFailureReleasesClaimAndCountsRetry() throws Exception {
        pendingEvent.markFailedAttempt("previous failure", 1);
        pendingEvent.setStatus(OutboxStatus.RETRYING);
        Exception failure = new RecordTooLargeException("too large");
        when(marker.claimFailedForRetry(OUTBOX_ID, null)).thenReturn(pendingEvent);
        when(producer.sendSync(eq(TOPIC), anyString(), any())).thenThrow(failure);

        assertThat(publisher().republishOne(OUTBOX_ID, null)).isEqualTo(OutboxPublishResult.FAILED);
        verify(marker).markManualRetryFailed(OUTBOX_ID, failure, true);
    }

    @Test
    void delegatesToMarkerMarkPublishedOnSuccessfulSend() throws Exception {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any()))
                .thenReturn(mock(SendResult.class));

        OutboxPublishResult result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxPublishResult.PUBLISHED);
        verify(marker).markPublished(OUTBOX_ID);
        verify(marker, never()).markFailedAttempt(any(), anyInt(), anyLong());
    }

    @Test
    void transientKafkaFailureKeepsPendingWithoutConsumingRetry() throws Exception {
        ExecutionException sendFailure = new ExecutionException(
                new org.apache.kafka.common.errors.TimeoutException("broker unavailable"));
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any()))
                .thenThrow(sendFailure);

        OutboxPublishResult result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxPublishResult.RETRY_LATER);
        verifyNoInteractions(marker);
    }

    @Test
    void permanentRecordFailureIsMarkedFailedImmediately() throws Exception {
        RecordTooLargeException sendFailure = new RecordTooLargeException("too large");
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any()))
                .thenThrow(sendFailure);
        when(marker.markFailedAttempt(sendFailure, 1, OUTBOX_ID)).thenReturn(OutboxStatus.FAILED);

        assertThat(publisher().publishOne(OUTBOX_ID)).isEqualTo(OutboxPublishResult.FAILED);
        verify(marker).markFailedAttempt(sendFailure, 1, OUTBOX_ID);
    }

    @Test
    void unknownFailureUsesConfiguredRetryLimit() throws Exception {
        RuntimeException sendFailure = new RuntimeException("unknown error");
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), any()))
                .thenThrow(sendFailure);
        when(marker.markFailedAttempt(sendFailure, MAX_RETRY, OUTBOX_ID)).thenReturn(OutboxStatus.PENDING);

        OutboxPublishResult result = publisher().publishOne(OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxPublishResult.RETRY_WITHOUT_PAUSE);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(marker).markFailedAttempt(exceptionCaptor.capture(), eq(MAX_RETRY), eq(OUTBOX_ID));
        assertThat(exceptionCaptor.getValue()).isSameAs(sendFailure);
        verify(marker, never()).markPublished(anyLong());
    }
}
