package com.sparta.trading.infrastructure.persistence.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CashLedgerRepositoryImpl implements CashLedgerRepository {

    private final CashLedgerJpaRepository cashLedgerJpaRepository;

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
}
