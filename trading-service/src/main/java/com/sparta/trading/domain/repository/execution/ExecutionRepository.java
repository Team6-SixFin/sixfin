package com.sparta.trading.domain.repository.execution;

import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {

    Optional<Executions> findByOrderId(UUID orderId);

    Executions save(Executions execution);

    Page<Executions> search(UUID userId, UUID positionId, Long stockId, String side, Pageable pageable);
}
