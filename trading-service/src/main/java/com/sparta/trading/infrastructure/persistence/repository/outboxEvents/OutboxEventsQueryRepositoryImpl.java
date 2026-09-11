package com.sparta.trading.infrastructure.persistence.repository.outboxEvents;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventsQueryRepositoryImpl implements OutboxEventsQueryRepository {

    private final OutboxEventsJpaRepository outboxEventJpaRepository;

    @Override
    public Page<OutboxEvents> searchOutbox(TradingAdminSearchOutboxEventQurey query, Pageable pageable) {
        return outboxEventJpaRepository.searchOutBox(
                query.status(),
                query.eventType(),
                query.minRetryCount(),
                query.from(),
                query.to(),
                query.includePayload(),
                pageable);
    }

    @Override
    public long countUnpublished() {
        return outboxEventJpaRepository.countUnpublished();
    }

    @Override
    public List<OutboxEvents> findUnpublished(Pageable pageable) {
        return outboxEventJpaRepository.findUnpublished(pageable);
    }

    @Override
    public List<Long> findPendingIds(int count) {
        return outboxEventJpaRepository.findPendingIds(count);
    }

    @Override
    public Optional<OutboxEvents> findById(long id) {
        return outboxEventJpaRepository.findById(id);
    }
}
