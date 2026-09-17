package com.sparta.trading.domain.repository.cashledgers;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

public interface CashLedgersQueryRepository {

    Slice<CashLedgers> findAllByAccountIdAndTxType(
            UUID accountId,
            CashLedgerTxType txType,
            Pageable pageable
    );

    List<LedgerSequenceMismatchGroup> findLedgerSequenceMismatches(UUID accountId);
}
