package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingAdminResetAccountResponse(
        UUID accountId,
        UUID userId,
        BigDecimal cashBalanceBefore,
        BigDecimal cashBalanceAfter,
        BigDecimal adjustmentAmount,
        Integer closedPositionCount,
        Long ledgerId,
        Instant resetAt
) {

    public static TradingAdminResetAccountResponse of(
            Accounts account,
            BigDecimal cashBalanceBefore,
            BigDecimal adjustmentAmount,
            int closedPositionCount,
            CashLedgers ledger,
            Instant resetAt
    ) {
        return new TradingAdminResetAccountResponse(
                account.getId(),
                account.getUserId(),
                cashBalanceBefore,
                account.getCashBalance(),
                adjustmentAmount,
                closedPositionCount,
                ledger.getId(),
                resetAt
        );
    }
}
