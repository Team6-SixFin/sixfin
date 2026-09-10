package com.sparta.trading.infrastructure.persistence.repository.cashledgers;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersRepository;
import com.sparta.trading.domain.repository.cashledgers.LedgerSequenceMismatchGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CashLedgerRepositoryImpl implements CashLedgersRepository {

    private final CashLedgersJpaRepository cashLedgerJpaRepository;

    @Override
    public CashLedgers save(CashLedgers cashLedger) {
        return cashLedgerJpaRepository.save(cashLedger);
    }

    @Override
    public Page<CashLedgers> findAllByAccountIdAndTxType(
            UUID accountId,
            CashLedgerTxType txType,
            Pageable pageable
    ) {
        return cashLedgerJpaRepository.findAllByAccountIdAndTxType(
                accountId,
                txType,
                pageable
        );
    }

    @Override
    public List<LedgerSequenceMismatchGroup> findLedgerSequenceMismatches(UUID accountId) {
        return cashLedgerJpaRepository.findLedgerSequenceMismatches(accountId);
    }
}
