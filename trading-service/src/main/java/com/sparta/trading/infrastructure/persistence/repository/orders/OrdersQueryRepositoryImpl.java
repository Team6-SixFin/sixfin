package com.sparta.trading.infrastructure.persistence.repository.orders;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.orders.DuplicateRequestGroup;
import com.sparta.trading.domain.repository.orders.OrdersQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrdersQueryRepositoryImpl implements OrdersQueryRepository {

    private final OrdersJpaRepository orderJpaRepository;

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

    @Override
    public Optional<Orders> findByRequestId(UUID requestId) {
        return orderJpaRepository.findByRequestId(requestId);
    }

    @Override
    public Optional<Orders> findById(UUID id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public List<DuplicateRequestGroup> findDuplicateRequestGroups(UUID accountId) {
        return orderJpaRepository.findDuplicateRequestGroups(accountId);
    }
}
