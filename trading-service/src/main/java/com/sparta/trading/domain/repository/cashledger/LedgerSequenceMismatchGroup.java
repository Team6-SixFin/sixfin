package com.sparta.trading.domain.repository.cashledger;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerSequenceMismatchGroup {
    UUID getAccountId();
    Long getLedgerId();
    BigDecimal getAmount();
    BigDecimal getBalanceAfter();
    BigDecimal getPrevBalanceAfter();
}
