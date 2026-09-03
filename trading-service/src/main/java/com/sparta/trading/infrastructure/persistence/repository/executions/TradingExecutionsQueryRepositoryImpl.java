package com.sparta.trading.infrastructure.persistence.repository.executions;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.repository.execution.TradingExecutionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TradingExecutionsQueryRepositoryImpl implements TradingExecutionQueryRepository {
    private final TradingExecutionsJpaRepository tradingExecutionsJpaRepository;

    @Override
    public Page<Executions> searchExecution(TradingAdminSearchExecutionQuery tradingExecutionQuery, Long targetStockId, Pageable pageable) {
        return tradingExecutionsJpaRepository.searchExexution(tradingExecutionQuery, targetStockId,pageable);
    }
}
