package com.sparta.trading.infrastructure.persistence.repository.executions;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

 interface TradingExecutionsJpaRepository extends JpaRepository<Executions, UUID> {
    Page<Executions> searchExexution(TradingAdminSearchExecutionQuery tradingExecutionQuery, Long targetStockId, Pageable pageable);
}
