package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
            new OutboxPublisherProperties("trade-events.v1", BATCH_SIZE, 5);

    @Test
    void publishesEachPendingIdWithConfiguredBatchSize() {
        when(outboxEventsQueryRepository.findPendingIds(BATCH_SIZE)).thenReturn(List.of(1L, 2L));
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties);

        schedular.publishPending();

        verify(outboxEventsQueryRepository).findPendingIds(BATCH_SIZE);
        verify(kafkaOutboxPublisher).publishOne(1L);
        verify(kafkaOutboxPublisher).publishOne(2L);
    }

    @Test
    void doesNothingWhenNoPendingIds() {
        when(outboxEventsQueryRepository.findPendingIds(BATCH_SIZE)).thenReturn(List.of());
        TradingKafkaOutboxPublishingSchedular schedular =
                new TradingKafkaOutboxPublishingSchedular(outboxEventsQueryRepository, kafkaOutboxPublisher, properties);

        schedular.publishPending();

        verifyNoInteractions(kafkaOutboxPublisher);
    }
}
