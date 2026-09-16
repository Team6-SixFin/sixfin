package com.sparta.trading.domain.repository.outboxEvents;

public record PendingOutboxEventsRef(
        Long id, String partitionKey
) {
}
