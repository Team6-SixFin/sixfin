package com.sparta.trading.domain.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CashLedgerRepository {

    CashLedgers save(CashLedgers cashLedger);

    Page<CashLedgers> findAllByAccountIdAndTxType(
            UUID accountId,
            CashLedgerTxType txType,
            Pageable pageable
    );

    List<LedgerSequenceMismatchGroup> findLedgerSequenceMismatches(UUID accountId);
}
