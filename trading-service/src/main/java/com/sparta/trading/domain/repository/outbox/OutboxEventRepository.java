package com.sparta.trading.domain.repository.outbox;

import com.sparta.trading.domain.entity.OutboxEvents;

public interface OutboxEventRepository {

    OutboxEvents save(OutboxEvents outboxEvent);
}
