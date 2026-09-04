package com.sparta.trading.infrastructure.persistence.repository.order;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final TradingOrderJpaRepository orderJpaRepository;

    @Override
    public Optional<Orders> findByRequestId(UUID requestId) {
        return orderJpaRepository.findByRequestId(requestId);
    }

    @Override
    public Orders save(Orders order) {
        return orderJpaRepository.save(order);
    }
}
