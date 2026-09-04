package com.sparta.trading.infrastructure.persistence.repository.order;

import com.sparta.trading.domain.entity.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

 interface TradingOrderJpaRepository extends JpaRepository<Orders, UUID> {

     Optional<Orders> findByRequestId(UUID requestId);

     @Query("""
        SELECT o FROM Orders o
        WHERE (:stockId IS NULL OR o.stockId = :stockId)
          AND (coalesce(:accountIds, null) IS NULL OR o.accountId IN :accountIds)
          AND (:side IS NULL OR o.side = :side)
          AND (:status IS NULL OR o.status = :status)
          AND (cast(:from as string) IS NULL OR o.createdAt >= :from)
          AND (cast(:to as string) IS NULL OR o.createdAt <= :to)
    """)
     Page<Orders> searchOrder(@Param("stockId") Long stockId,
                              @Param("accountIds") List<UUID> accountIds,
                              @Param("side") String side,
                              @Param("status") String status,
                              @Param("from") Instant from,
                              @Param("to") Instant to,
                              Pageable pageable);
 }
