package com.sparta.trading.domain.repository.cashledgers;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerSequenceMismatchGroup {
    UUID getAccountId();
    Long getLedgerId();
    BigDecimal getAmount();
    BigDecimal getBalanceAfter();
    BigDecimal getPrevBalanceAfter();
}
