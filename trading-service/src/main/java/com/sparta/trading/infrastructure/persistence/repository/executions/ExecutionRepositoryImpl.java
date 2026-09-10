package com.sparta.trading.infrastructure.persistence.repository.execution;

import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.repository.execution.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ExecutionRepositoryImpl implements ExecutionRepository {

    private final ExecutionJpaRepository executionJpaRepository;

    @Override
    public Optional<Executions> findByOrderId(UUID orderId) {
        return executionJpaRepository.findByOrderId(orderId);
    }

    @Override
    public Executions save(Executions execution) {
        return executionJpaRepository.save(execution);
    }

    @Override
    public Page<Executions> search(UUID userId, UUID positionId, Long stockId, String side, Pageable pageable) {
        return executionJpaRepository.search(userId, positionId, stockId, side, pageable);
    }
}
