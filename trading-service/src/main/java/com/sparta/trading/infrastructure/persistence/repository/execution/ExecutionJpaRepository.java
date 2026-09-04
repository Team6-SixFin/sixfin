package com.sparta.trading.infrastructure.persistence.repository.execution;

import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ExecutionJpaRepository extends JpaRepository<Executions, UUID> {

    Optional<Executions> findByOrderId(UUID orderId);
}
