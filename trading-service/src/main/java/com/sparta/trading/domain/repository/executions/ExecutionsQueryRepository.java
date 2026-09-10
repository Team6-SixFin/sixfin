package com.sparta.trading.domain.repository.executions;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExecutionsQueryRepository {

    Page<Executions> searchExecution(TradingAdminSearchExecutionQuery tradingExecutionQuery,
                                     Long targetStockId,
                                     Pageable pageable);
}
