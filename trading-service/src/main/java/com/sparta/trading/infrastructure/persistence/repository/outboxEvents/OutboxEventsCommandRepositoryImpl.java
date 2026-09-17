package com.sparta.trading.infrastructure.persistence.repository.outboxEvents;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventsCommandRepositoryImpl implements OutboxEventsCommandRepository {

    private final OutboxEventsJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvents save(OutboxEvents outboxEvent) {
        return outboxEventJpaRepository.save(outboxEvent);
    }

    @Override
    public Optional<OutboxEvents> findByIdForUpdate(long id) {
        return outboxEventJpaRepository.findById(id);
    }

}
