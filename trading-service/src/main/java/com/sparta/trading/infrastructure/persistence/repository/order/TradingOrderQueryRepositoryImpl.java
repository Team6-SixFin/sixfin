package com.sparta.trading.infrastructure.persistence.repository.order;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TradingOrderQueryRepositoryImpl implements TradingOrderQueryRepository {

    private final TradingOrderJpaRepository orderJpaRepository;

    @Override
    public Page<Orders> searchOrder(
            TradingAdminSearchOrderQuery query,
            Long stockId,
            List<UUID> accountIds,
            Pageable pageable) {
        return orderJpaRepository.searchOrder(stockId,
                accountIds,
                query.side(),
                query.status(),
                query.from(),
                query.to(),
                pageable);
    }
}
