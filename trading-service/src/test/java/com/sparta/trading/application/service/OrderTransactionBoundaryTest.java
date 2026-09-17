package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.port.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTransactionBoundaryTest {

    @Test
    void preValidationDoesNotOpenTransactionAndOrderExecutionOwnsTransaction() throws Exception {
        Method placeOrder = TradingCommandService.class
                .getMethod("placeOrder", UUID.class, PlaceOrderCommand.class);
        Method executeOrder = OrderExecutionService.class
                .getMethod("execute", UUID.class, NormalizedOrder.class, Quote.class);

        assertThat(placeOrder.getAnnotation(Transactional.class)).isNull();
        assertThat(executeOrder.getAnnotation(Transactional.class)).isNotNull();
    }
}
