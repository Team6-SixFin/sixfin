package com.sparta.trading.domain.repository.outboxEvents;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TradingOutboxEventsQueryRepository {

    Page<OutboxEvents> searchOutbox(TradingAdminSearchOutboxEventQurey tradingAdminSearchOutboxEventQurey, Pageable pageable);

    long countUnpublished();

    List<OutboxEvents> findUnpublished(Pageable pageable);

    List<Long> findPendingIds(int count);
}
