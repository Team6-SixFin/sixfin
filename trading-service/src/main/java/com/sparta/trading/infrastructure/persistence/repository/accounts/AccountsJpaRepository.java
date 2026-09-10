package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.CashLedgersAccountsGroup;
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

     @Query("SELECT COUNT(a) FROM Accounts a WHERE a.cashBalance < 0 AND (:accountId IS NULL OR a.id = :accountId)")
     long countNegativeCashBalance(UUID accountId);

     @Query("SELECT a FROM Accounts a WHERE a.cashBalance < 0 AND (:accountId IS NULL OR a.id = :accountId)")
     List<Accounts> findNegativeCashBalance(UUID accountId, Pageable pageable);

     // 계좌별로 SUM(CashLedger.amount)과 accounts.CashBalance를 구함.
     @Query("""
        SELECT a.id as accountId, a.cashBalance as cashBalance, a.userId AS userId,
                coalesce(sum(cl.amount), 0) as ledgerSum
        FROM Accounts a
                LEFT JOIN CashLedgers cl ON cl.account = a
        WHERE (:accountId IS NULL OR a.id = :accountId)
        GROUP BY a.id, a.cashBalance, a.userId
        HAVING a.cashBalance <> COALESCE(sum(cl.amount), 0)
        """)
     List<CashLedgersAccountsGroup> findLedgerBalanceMismatches(UUID accountId);
 }
