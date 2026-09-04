package com.sparta.trading.infrastructure.persistence.repository.account;

import com.sparta.trading.domain.entity.Accounts;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface AccountJpaRepository extends JpaRepository<Accounts, UUID> {

    Optional<Accounts> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Accounts a where a.userId = :userId")
    Optional<Accounts> findByUserIdForUpdate(UUID userId);
}
