package com.sparta.trading.domain.repository.execution;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TradingExecutionQueryRepository {

    Page<Executions> searchExecution(TradingAdminSearchExecutionQuery tradingExecutionQuery,
                                     Long targetStockId,
                                     Pageable pageable);
}
