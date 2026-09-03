package com.sparta.trading.infrastructure.persistence.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CashLedgerRepositoryImpl implements CashLedgerRepository {

    private final CashLedgerJpaRepository cashLedgerJpaRepository;

    @Override
    public CashLedgers save(CashLedgers cashLedger) {
        return cashLedgerJpaRepository.save(cashLedger);
    }
}
