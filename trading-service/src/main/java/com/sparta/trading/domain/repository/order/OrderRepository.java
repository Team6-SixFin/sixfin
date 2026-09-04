package com.sparta.trading.domain.repository.order;

import com.sparta.trading.domain.entity.Orders;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Optional<Orders> findByRequestId(UUID requestId);

    Orders save(Orders order);
}
