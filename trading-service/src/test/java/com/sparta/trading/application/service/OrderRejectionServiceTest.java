package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.orders.OrdersCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderRejectionServiceTest {

    @Mock
    private OrdersCommandRepository ordersCommandRepository;

    @InjectMocks
    private OrderRejectionService orderRejectionService;

    @Test
    void record_flushesRejectedOrderToDetectRequestIdConflicts() {
        Orders rejectedOrder = org.mockito.Mockito.mock(Orders.class);

        orderRejectionService.record(rejectedOrder);

        verify(ordersCommandRepository).saveAndFlush(rejectedOrder);
    }
}
