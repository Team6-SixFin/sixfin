package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

 interface TradingAccountsJpaRepository extends JpaRepository<Accounts, UUID> {

    //계좌 전체 조회
    Page<Accounts> search(UUID userId, Pageable pageable);
}
