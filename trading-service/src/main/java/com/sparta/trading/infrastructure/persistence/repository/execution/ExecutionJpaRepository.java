package com.sparta.trading.infrastructure.persistence.repository.execution;

import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ExecutionJpaRepository extends JpaRepository<Executions, UUID> {

    Optional<Executions> findByOrderId(UUID orderId);

    @Query("""
        SELECT e FROM Executions e
        WHERE e.userId = :userId
          AND (:positionId IS NULL OR e.positionId = :positionId)
          AND (:stockId IS NULL OR e.stockId = :stockId)
          AND (:side IS NULL OR e.side = :side)
    """)
    Page<Executions> search(@Param("userId") UUID userId,
                             @Param("positionId") UUID positionId,
                             @Param("stockId") Long stockId,
                             @Param("side") String side,
                             Pageable pageable);
}
