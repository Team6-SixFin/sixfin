package com.sparta.trading.domain.repository.order;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradingOrderQueryRepository {
    Page<Orders> searchOrder(Pageable pageable);
}
