package com.sparta.trading.domain.repository.order;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TradingOrderQueryRepository {
    Page<Orders> searchOrder(
            TradingAdminSearchOrderQuery query,
            Long stockId,
            List<UUID> accountIds,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}
