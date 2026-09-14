package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.orders.OrdersCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRejectionService {

    private final OrdersCommandRepository ordersCommandRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orders record(Orders rejectedOrder) {
        return ordersCommandRepository.saveAndFlush(rejectedOrder);
    }
}
