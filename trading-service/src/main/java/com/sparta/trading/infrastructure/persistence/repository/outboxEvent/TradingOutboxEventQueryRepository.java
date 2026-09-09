package com.sparta.trading.infrastructure.persistence.repository.outboxEvent;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvent.TradingOutboxEventsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TradingOutboxEventQueryRepository implements TradingOutboxEventsQueryRepository {

    private final TradingOutboxEventJpaRepository tradingOutboxEventJpaRepository;

    @Override
    public Page<OutboxEvents> searchOutbox(TradingAdminSearchOutboxEventQurey query, Pageable pageable) {
        return tradingOutboxEventJpaRepository.searchOutBox(
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
        return tradingOutboxEventJpaRepository.countUnpublished();
    }

    @Override
    public List<OutboxEvents> findUnpublished(Pageable pageable) {
        return tradingOutboxEventJpaRepository.findUnpublished(pageable);
    }

    @Override
    public List<Long> findPendingIds(int count) {
        return tradingOutboxEventJpaRepository.findPendingIds(count);
    }
}
