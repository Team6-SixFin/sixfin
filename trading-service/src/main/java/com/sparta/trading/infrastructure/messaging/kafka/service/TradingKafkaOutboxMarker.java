package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsCommandRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TradingKafkaOutboxMarker {

    private final OutboxEventsCommandRepository outboxEventsCommandRepository;

    @Transactional
    public void markPublished(long id){
        OutboxEvents outboxEvents = outboxEventsCommandRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));
        outboxEvents.markPublished(Instant.now());
    }

    @Transactional
    public void markFailedAttempt(Exception e, int maxRetry, long id) {
        OutboxEvents outboxEvents = outboxEventsCommandRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));
        outboxEvents.markFailedAttempt(e.getMessage(), maxRetry);
    }
}
