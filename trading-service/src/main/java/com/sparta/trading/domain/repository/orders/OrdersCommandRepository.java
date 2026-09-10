package com.sparta.trading.domain.repository.orders;

import com.sparta.trading.domain.entity.Orders;

public interface OrdersCommandRepository {

    Orders save(Orders order);

}
