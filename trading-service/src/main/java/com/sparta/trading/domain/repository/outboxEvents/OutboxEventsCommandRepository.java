package com.sparta.trading.domain.repository.outboxEvents;

import com.sparta.trading.domain.entity.OutboxEvents;
import java.util.Optional;

public interface OutboxEventsCommandRepository {

    OutboxEvents save(OutboxEvents outboxEvent);
    Optional<OutboxEvents> findByIdForUpdate(long id);

    Optional<OutboxEvents> claim(long id);
}
