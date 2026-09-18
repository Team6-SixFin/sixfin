package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.OutboxStatus;
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
    public OutboxStatus markFailedAttempt(Exception e, int maxRetry, long id) {
        OutboxEvents outboxEvents = outboxEventsCommandRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));
        outboxEvents.markFailedAttempt(e.getMessage(), maxRetry);
        return outboxEvents.getStatus();
    }

    @Transactional
    public OutboxEvents claimFailedForRetry(long id, JsonNode replacementPayload) {
        OutboxEvents outboxEvents = outboxEventsCommandRepository.claim(id)
            .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));

        if (outboxEvents.getStatus() != OutboxStatus.FAILED) {
            throw new CustomException(TradingErrorCode.OUTBOX_NOT_FAILED);
        }
        if (replacementPayload != null) outboxEvents.overwritePayload(replacementPayload);
        outboxEvents.setStatus(OutboxStatus.RETRYING);
        return outboxEvents;
    }

    @Transactional
    public void markManualRetryFailed(long id, Exception error, boolean countAttempt) {
        OutboxEvents outboxEvents = outboxEventsCommandRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(TradingErrorCode.OUTBOX_EVENT_NOT_FOUND));
        outboxEvents.failManualRetry(error.getMessage(), countAttempt);
    }
}
