package com.sparta.trading.domain.repository.accounts;

import java.math.BigDecimal;
import java.util.UUID;

public interface CashLedgersAccountsGroup {
    UUID getAccountId();
    BigDecimal getCashBalance();
    UUID getUserId();
    BigDecimal getLedgerSum();
}
