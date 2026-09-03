package com.sparta.trading.infrastructure.persistence.repository.account;

import com.sparta.trading.domain.entity.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountJpaRepository extends JpaRepository<Accounts, UUID> {

    Optional<Accounts> findByUserId(UUID userId);
}
