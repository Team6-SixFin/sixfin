package com.sparta.trading.infrastructure.persistence.repository.position;

import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.position.DuplicateOpenPositionGroup;
import com.sparta.trading.domain.repository.position.PositionQuantityMismatchGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PositionJpaRepository extends JpaRepository<Positions, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Positions p where p.accountId = :accountId and p.stockId = :stockId and p.status = 'OPEN'")
    Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId);

    @Query("""
            select p from Positions p
                        where p.accountId = :accountId
                          and p.status = 'OPEN'
                          and p.deletedAt is null
            """)
    List<Positions> findAllOpenByAccountId(UUID accountId);

    @Query("""
            select p from Positions p
            where p.userId = :userId
              and p.status = :status
              and p.deletedAt is null
            order by p.openedAt desc, p.id desc
            """)
    Page<Positions> findAllByUserIdAndStatus(
            UUID userId,
            String status,
            Pageable pageable
    );

    Optional<Positions> findByIdAndUserIdAndDeletedAtIsNull(
            UUID positionId,
            UUID userId
    );
           
    List<Positions> findAllByAccountIdAndStatus(UUID id, String status);

    @Query("""
            SELECT count(p) FROM Positions p
            WHERE p.quantity < 0 AND p.deletedAt IS NULL
              AND (:accountId IS NULL OR p.accountId = :accountId)
            """)
    long countNegativeQuantityByAccountId(UUID accountId);

    @Query("""
            SELECT p FROM Positions p
            WHERE p.quantity < 0 AND p.deletedAt IS NULL
              AND (:accountId IS NULL OR p.accountId = :accountId)
            """)
    List<Positions> findNegativeQuantityByAccountId(UUID accountId, Pageable pageable);

    @Query("""
            SELECT p.accountId AS accountId, p.stockId AS stockId, COUNT(p) AS duplicateCount
            FROM Positions p
            WHERE p.status = 'OPEN' AND p.deletedAt IS NULL
              AND (:accountId IS NULL OR p.accountId = :accountId)
            GROUP BY p.accountId, p.stockId
            HAVING COUNT(p) >= 2
            """)
    List<DuplicateOpenPositionGroup> findDuplicateOpenPositionGroups(UUID accountId);

    @Query("""
            SELECT p.id as positionId, p.accountId as accountId, p.quantity AS positionQuantity,
                        COALESCE(sum(CASE WHEN e.side = 'BUY' THEN e.executedQuantity ELSE -e.executedQuantity END) ,0) as executionNetQuantity
            FROM Positions p
                        LEFT JOIN Executions e ON e.positionId = p.id
            WHERE p.deletedAt IS NULL
              AND (:accountId IS NULL OR p.accountId = :accountId)
            GROUP BY p.id, p.accountId, p.quantity
            HAVING p.quantity <> COALESCE(
                        sum(CASE WHEN e.side = 'BUY' THEN e.executedQuantity ELSE - e.executedQuantity END), 0)
            """)
    List<PositionQuantityMismatchGroup> findPositionQuantityMismatches(UUID accountId);
}
