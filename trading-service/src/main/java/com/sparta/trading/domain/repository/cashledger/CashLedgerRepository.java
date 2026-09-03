package com.sparta.trading.domain.repository.cashledger;

import com.sparta.trading.domain.entity.CashLedgers;

public interface CashLedgerRepository {

    CashLedgers save(CashLedgers cashLedger);
}
