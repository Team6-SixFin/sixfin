package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

 interface TradingAccountsJpaRepository extends JpaRepository<Accounts, UUID> {

    //계좌 전체 조회
    @Query("SELECT a FROM Accounts a WHERE (:userId IS NULL OR a.userId = :userId)")
    Page<Accounts> search(UUID userId, Pageable pageable);

     List<Accounts> findAllByUserId(UUID userId);

     Optional<Accounts> findByUserId(UUID userId);
 }
