package com.sparta.trading.infrastructure.persistence.repository.executions;

import com.sparta.trading.application.dto.query.TradingAdminSearchExecutionQuery;
import com.sparta.trading.domain.entity.Executions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

 interface TradingExecutionsJpaRepository extends JpaRepository<Executions, UUID> {

     @Query("""
        SELECT e FROM Executions e
        WHERE (:userId IS NULL OR e.userId = :userId)
          AND (:positionId IS NULL OR e.positionId = :positionId)
          AND (:stockId IS NULL OR e.stockId = :stockId)
          AND (:side IS NULL OR e.side = :side)
          AND (cast(:from as string) IS NULL OR e.createdAt >= :from)
          AND (cast(:to as string) IS NULL OR e.createdAt <= :to)
          AND e.deletedAt IS NULL
    """)
     Page<Executions> searchExecution(
             @Param("userId") UUID userId,
             @Param("positionId") UUID positionId,
             @Param("stockId") Long stockId,
             @Param("side") String side,
             @Param("from") Instant from,
             @Param("to") Instant to,
             Pageable pageable
     );
}
