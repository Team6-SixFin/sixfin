package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsCommandRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * publishOne이 트랜잭션 밖으로 카프카 전송을 뺀 뒤, 실제 상태 전이(PENDING→PUBLISHED/FAILED)를
 * 별도 트랜잭션으로 기록하는 TradingKafkaOutboxMarker의 동작을 검증한다.
 * (원래 TradingKafkaOutboxPublisherTest에 있던 상태 전이 검증이 이 파일로 옮겨온 것)
 */
@ExtendWith(MockitoExtension.class)
class TradingKafkaOutboxMarkerTest {

    private static final long OUTBOX_ID = 1L;

    @Mock
    private OutboxEventsCommandRepository outboxEventsCommandRepository;

    private OutboxEvents pendingEvent;
    private TradingKafkaOutboxMarker marker;

    @BeforeEach
    void setUp() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode().put("stub", true);
        pendingEvent = OutboxEvents.buyExecuted(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), payload, Instant.now());
        marker = new TradingKafkaOutboxMarker(outboxEventsCommandRepository);
    }

    @Test
    void markPublishedTransitionsPendingToPublished() {
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        marker.markPublished(OUTBOX_ID);

        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(pendingEvent.getPublishedAt()).isNotNull();
    }

    @Test
    void markPublishedThrowsWhenOutboxEventNotFound() {
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marker.markPublished(OUTBOX_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND);
    }

    @Test
    void markFailedAttemptStaysPendingBelowMaxRetry() {
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        OutboxStatus result = marker.markFailedAttempt(new RuntimeException("broker unavailable"), 5, OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
        assertThat(pendingEvent.getLastError()).isEqualTo("broker unavailable");
    }

    @Test
    void markFailedAttemptTransitionsToFailedWhenMaxRetryReached() {
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        OutboxStatus result = marker.markFailedAttempt(new RuntimeException("broker unavailable"), 1, OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(1);
    }

    @Test
    void markFailedAttemptOnAlreadyFailedEventDoesNotThrow() {
        pendingEvent.markFailedAttempt("first failure", 1); // 이미 FAILED로 만들어둠
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.of(pendingEvent));

        OutboxStatus result = marker.markFailedAttempt(new RuntimeException("retry failed again"), 1, OUTBOX_ID);

        assertThat(result).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(pendingEvent.getRetryCount()).isEqualTo(2);
        assertThat(pendingEvent.getLastError()).isEqualTo("retry failed again");
    }

    @Test
    void markFailedAttemptThrowsWhenOutboxEventNotFound() {
        when(outboxEventsCommandRepository.findByIdForUpdate(OUTBOX_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marker.markFailedAttempt(new RuntimeException("x"), 5, OUTBOX_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND);
    }
}
