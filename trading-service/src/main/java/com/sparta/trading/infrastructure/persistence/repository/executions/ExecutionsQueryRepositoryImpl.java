package com.sparta.trading.infrastructure.persistence.repository.executions;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.repository.executions.ExecutionsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExecutionsQueryRepositoryImpl implements ExecutionsQueryRepository {
    private final ExecutionsJpaRepository tradingExecutionsJpaRepository;

    @Override
    public Page<Executions> searchExecution(
            TradingAdminSearchExecutionQuery query,
            Long targetStockId,
            Pageable pageable
    ) {
        return tradingExecutionsJpaRepository.searchExecution(
                query.userId(),
                query.positionId(),
                targetStockId,
                query.side(),
                query.from(),
                query.to(),
                pageable
        );
    }
}
