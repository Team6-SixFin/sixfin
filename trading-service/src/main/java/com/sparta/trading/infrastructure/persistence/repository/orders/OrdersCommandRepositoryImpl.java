package com.sparta.trading.infrastructure.persistence.repository.orders;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.orders.OrdersCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrdersCommandRepositoryImpl implements OrdersCommandRepository {

    private final OrdersJpaRepository orderJpaRepository;

    @Override
    public Orders save(Orders order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Orders saveAndFlush(Orders order) {
        return orderJpaRepository.saveAndFlush(order);
    }

}
