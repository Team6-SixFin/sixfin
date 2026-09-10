package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import com.sparta.trading.infrastructure.messaging.kafka.producer.TradingKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * publishOne의 상태 전이(성공/실패/재시도 임계치)를 실제 Kafka 없이 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TradingKafkaOutboxPublisherTest {

    private static final Long OUTBOX_ID = 1L;
    private static final String TOPIC = "trade-events.v1";

    @Mock
    private OutboxEventsRepository outboxEventsRepository;

    @Mock
    private TradingKafkaProducer producer;

    private OutboxEvents pendingEvent;

    @BeforeEach
    void setUp() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("stub", true);
        pendingEvent = OutboxEvents.buyExecuted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), payload, Instant.now());
    }

    private TradingKafkaOutboxPublisher publisherWithMaxRetry(int maxRetry) {
        return new TradingKafkaOutboxPublisher(
                outboxEventsRepository, producer, new OutboxPublisherProperties(TOPIC, 100, maxRetry));
    }

    @Test
    void throwsWhenOutboxEventNotFound() {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.empty());
        TradingKafkaOutboxPublisher publisher = publisherWithMaxRetry(5);

        assertThatThrownBy(() -> publisher.publishOne(OUTBOX_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND);
    }

    @Test
    void skipsSendWhenStatusIsNotPending() throws Exception {
        pendingEvent.markPublished(Instant.now());
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        TradingKafkaOutboxPublisher publisher = publisherWithMaxRetry(5);

        publisher.publishOne(OUTBOX_ID);

        verify(producer, never()).sendSync(anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyLong());
    }

    @Test
    void marksPublishedOnSuccessfulSend() throws Exception {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), org.mockito.ArgumentMatchers.any(), anyLong()))
                .thenReturn(mock(SendResult.class));
        TradingKafkaOutboxPublisher publisher = publisherWithMaxRetry(5);

        publisher.publishOne(OUTBOX_ID);

        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(pendingEvent.getPublishedAt()).isNotNull();
        assertThat(pendingEvent.getRetryCount()).isZero();
    }

    @Test
    void marksFailedAttemptOnSendFailureBelowMaxRetry() throws Exception {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), org.mockito.ArgumentMatchers.any(), anyLong()))
                .thenThrow(new RuntimeException("broker unavailable"));
        TradingKafkaOutboxPublisher publisher = publisherWithMaxRetry(5);

        publisher.publishOne(OUTBOX_ID);

        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
        assertThat(pendingEvent.getLastError()).isEqualTo("broker unavailable");
    }

    @Test
    void transitionsToFailedWhenMaxRetryReached() throws Exception {
        when(outboxEventsRepository.findById(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));
        when(producer.sendSync(eq(TOPIC), anyString(), org.mockito.ArgumentMatchers.any(), anyLong()))
                .thenThrow(new RuntimeException("broker unavailable"));
        TradingKafkaOutboxPublisher publisher = publisherWithMaxRetry(1);

        publisher.publishOne(OUTBOX_ID);

        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
    }
}
