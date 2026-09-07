package com.sparta.trading.domain.repository.outboxEvent;

import com.sparta.trading.application.dto.query.TradingAdminSearchOutboxEventQurey;
import com.sparta.trading.domain.entity.OutboxEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradingOutboxEventsQueryRepository {

    Page<OutboxEvents> searchOutbox(TradingAdminSearchOutboxEventQurey tradingAdminSearchOutboxEventQurey, Pageable pageable);

}
