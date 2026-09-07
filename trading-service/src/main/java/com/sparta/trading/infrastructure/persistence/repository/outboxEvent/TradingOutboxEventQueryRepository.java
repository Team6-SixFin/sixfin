package com.sparta.trading.infrastructure.persistence.repository.outboxEvent;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.repository.outboxEvent.TradingOutboxEventsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
}
