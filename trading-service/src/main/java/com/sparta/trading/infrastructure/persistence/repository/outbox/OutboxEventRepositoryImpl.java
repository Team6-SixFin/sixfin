package com.sparta.trading.infrastructure.persistence.repository.outbox;

import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvents save(OutboxEvents outboxEvent) {
        return outboxEventJpaRepository.save(outboxEvent);
    }

    @Override
    public Optional<OutboxEvents> findById(long id) {
        return outboxEventJpaRepository.findById(id);
    }
}
