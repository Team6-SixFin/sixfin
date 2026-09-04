package com.sparta.trading.domain.repository.execution;

import com.sparta.trading.domain.entity.Executions;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {

    Optional<Executions> findByOrderId(UUID orderId);

    Executions save(Executions execution);
}
