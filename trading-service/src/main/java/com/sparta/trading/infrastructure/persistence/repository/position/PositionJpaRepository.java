package com.sparta.trading.infrastructure.persistence.repository.position;

import com.sparta.trading.domain.entity.Positions;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PositionJpaRepository extends JpaRepository<Positions, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Positions p where p.accountId = :accountId and p.stockId = :stockId and p.status = 'OPEN'")
    Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId);
}
