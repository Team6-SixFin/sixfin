package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class TradingKafkaOutboxPublishingSchedular {

    private final OutboxEventsQueryRepository outboxEventsQueryRepository;
    private final TradingKafkaOutboxPublisher kafkaOutboxPublisher;
    private final OutboxPublisherProperties outboxPublisherProperties;

    @Scheduled(fixedDelayString = "3000")
    public void publishPending(){
        List<Long> pendingIds = outboxEventsQueryRepository.findPendingIds(outboxPublisherProperties.batchSize());
        for (Long pendingId : pendingIds) {
            kafkaOutboxPublisher.publishOne(pendingId);
        }
    }

}
