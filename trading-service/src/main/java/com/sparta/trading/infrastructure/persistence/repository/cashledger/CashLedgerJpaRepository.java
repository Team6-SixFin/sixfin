package com.sparta.trading.infrastructure.persistence.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgers;
import org.springframework.data.jpa.repository.JpaRepository;

interface CashLedgerJpaRepository extends JpaRepository<CashLedgers, Long> {
}
