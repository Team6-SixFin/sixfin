package com.sparta.trading.infrastructure.persistence.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface CashLedgerJpaRepository extends JpaRepository<CashLedgers, Long> {

    @Query("""
            select cashLedger
            from CashLedgers cashLedger
            where cashLedger.account.id = :accountId
              and (:txType is null or cashLedger.txType = :txType)
            order by cashLedger.createdAt desc, cashLedger.id desc
            """)
    Page<CashLedgers> findAllByAccountIdAndTxType(
            @Param("accountId") UUID accountId,
            @Param("txType") CashLedgerTxType txType,
            Pageable pageable
    );
}
