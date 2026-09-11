package com.sparta.trading.domain.repository.outboxEvents;

import com.sparta.trading.domain.entity.OutboxEvents;

public interface OutboxEventsCommandRepository {

    OutboxEvents save(OutboxEvents outboxEvent);
}
