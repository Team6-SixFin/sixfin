package com.sparta.trading.domain.repository.outboxEvents;

import com.sparta.trading.domain.entity.OutboxEvents;

import java.util.Optional;

public interface OutboxEventRepository {

    OutboxEvents save(OutboxEvents outboxEvent);

    Optional<OutboxEvents> findById(long id);
}
