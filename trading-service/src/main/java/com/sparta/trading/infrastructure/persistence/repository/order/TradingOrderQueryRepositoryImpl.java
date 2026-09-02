package com.sparta.trading.infrastructure.persistence.repository.order;

import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradingOrderQueryRepositoryImpl implements TradingOrderQueryRepository {

    private final TradingOrderJpaRepository orderJpaRepository;

    @Override
    public Page<Orders> searchOrder(Pageable pageable) {
        return orderJpaRepository.searchOrder(pageable);
    }
}
