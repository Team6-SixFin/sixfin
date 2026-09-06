package com.sparta.trading.infrastructure.persistence.repository.position;

import com.sparta.trading.domain.entity.Positions;
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
}
