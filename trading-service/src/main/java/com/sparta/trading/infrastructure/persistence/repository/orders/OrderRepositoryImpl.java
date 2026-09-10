package com.sparta.trading.infrastructure.persistence.repository.orders;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.orders.DuplicateRequestGroup;
import com.sparta.trading.domain.repository.orders.OrdersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrdersRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Optional<Orders> findByRequestId(UUID requestId) {
        return orderJpaRepository.findByRequestId(requestId);
    }

    @Override
    public Optional<Orders> findById(UUID id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Orders save(Orders order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public List<DuplicateRequestGroup> findDuplicateRequestGroups(UUID accountId) {
        return orderJpaRepository.findDuplicateRequestGroups(accountId);
    }
}
