package com.sparta.trading.infrastructure.persistence.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.cashledger.LedgerSequenceMismatchGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query(value = """
            SELECT account_id, id AS ledger_id, amount, balance_after, prev_balance_after
            FROM (
                SELECT account_id, id, amount, balance_after,
                               LAG(balance_after)
                               OVER (
                                      PARTITION BY account_id ORDER BY created_at, id
                                  )
                                      AS prev_balance_after
                FROM trading_service.p_cash_ledgers
                WHERE (:accountId IS NULL OR account_id = :accountId)
                ) as pcl
            WHERE balance_after <> amount + prev_balance_after
   """, nativeQuery = true)
    List<LedgerSequenceMismatchGroup> findLedgerSequenceMismatches(UUID accountId);
}
