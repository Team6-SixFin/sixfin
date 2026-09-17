package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.outboxEvents.PendingOutboxEventsRef;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * 스케줄러가 설정된 batchSize로 조회하고, 조회된 id마다 publishOne을 위임하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TradingKafkaOutboxPublishingSchedularTest {

    private static final int BATCH_SIZE = 2;

    @Mock
    private OutboxEventsQueryRepository outboxEventsQueryRepository;

    @Mock
    private TradingKafkaOutboxPublisher kafkaOutboxPublisher;

    private final OutboxPublisherProperties properties =
            new OutboxPublisherProperties("trade-events.v1", BATCH_SIZE, 5, 4, 5000);
    private final MutableClock clock = new MutableClock();

    @Test
    void publishesEachPendingIdWithConfiguredBatchSize() {
        List<PendingOutboxEventsRef> list = List.of(
                new PendingOutboxEventsRef(1L, "123e4567-e89b-12d3-a456-426614174000"),
                new PendingOutboxEventsRef(2L, "123e4567-e89b-12d3-a456-426614174000")
                );
        when(outboxEventsQueryRepository.findPendingRefs(BATCH_SIZE)).thenReturn(list);
        when(kafkaOutboxPublisher.publishOne(anyLong())).thenReturn(OutboxPublishResult.PUBLISHED);
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties, clock);

        schedular.publishPending();

        verify(outboxEventsQueryRepository).findPendingRefs(BATCH_SIZE);
        verify(kafkaOutboxPublisher).publishOne(1L);
        verify(kafkaOutboxPublisher).publishOne(2L);
    }

    @Test
    void skipsRemainingEventsInSameGroupAfterFailure() {
        List<PendingOutboxEventsRef> list = List.of(
                new PendingOutboxEventsRef(1L, "user-1"),
                new PendingOutboxEventsRef(2L, "user-1")
        );
        when(outboxEventsQueryRepository.findPendingRefs(BATCH_SIZE)).thenReturn(list);
        when(kafkaOutboxPublisher.publishOne(1L)).thenReturn(OutboxPublishResult.RETRY_LATER);
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties, clock);

        schedular.publishPending();

        verify(kafkaOutboxPublisher).publishOne(1L);
        verify(kafkaOutboxPublisher, never()).publishOne(2L);

        schedular.publishPending();
        verify(outboxEventsQueryRepository).findPendingRefs(BATCH_SIZE);
        clock.advanceMillis(5000);
        schedular.publishPending();
        verify(outboxEventsQueryRepository, times(2)).findPendingRefs(BATCH_SIZE);
    }

    @Test
    void continuesSameUserAfterPermanentFailure() {
        when(outboxEventsQueryRepository.findPendingRefs(BATCH_SIZE)).thenReturn(List.of(
                new PendingOutboxEventsRef(1L, "user-1"),
                new PendingOutboxEventsRef(2L, "user-1")));
        when(kafkaOutboxPublisher.publishOne(1L)).thenReturn(OutboxPublishResult.FAILED);
        when(kafkaOutboxPublisher.publishOne(2L)).thenReturn(OutboxPublishResult.PUBLISHED);
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties, clock);

        schedular.publishPending();

        verify(kafkaOutboxPublisher).publishOne(2L);
    }

    @Test
    void doesNothingWhenNoPendingIds() {
        when(outboxEventsQueryRepository.findPendingRefs(BATCH_SIZE)).thenReturn(List.of());
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties, clock);

        schedular.publishPending();

        verifyNoInteractions(kafkaOutboxPublisher);
    }

    private static class MutableClock extends Clock {
        private final AtomicLong now = new AtomicLong(1_000);

        void advanceMillis(long millis) {
            now.addAndGet(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    }
}
