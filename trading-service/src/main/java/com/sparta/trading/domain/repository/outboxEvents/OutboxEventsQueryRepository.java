package com.sparta.trading.domain.repository.outboxEvents;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OutboxEventsQueryRepository {

    Page<OutboxEvents> searchOutbox(TradingAdminSearchOutboxEventQurey tradingAdminSearchOutboxEventQurey, Pageable pageable);

    long countUnpublished();

    List<OutboxEvents> findUnpublished(Pageable pageable);

    List<Long> findPendingIds(int count);

    Optional<OutboxEvents> findById(long id);
}
