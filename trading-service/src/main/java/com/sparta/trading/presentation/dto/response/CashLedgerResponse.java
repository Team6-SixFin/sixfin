package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashLedgerResponse(
        Long ledgerId,
        UUID executionId,
        CashLedgerTxType txType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
    public static CashLedgerResponse from(CashLedgers cashLedgers) {
        return new CashLedgerResponse(
                cashLedgers.getId(),
                cashLedgers.getExecutionId(),
                cashLedgers.getTxType(),
                cashLedgers.getAmount(),
                cashLedgers.getBalanceAfter(),
                cashLedgers.getCreatedAt()
        );
    }
}
