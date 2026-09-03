package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Accounts;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        BigDecimal cashBalance,
        BigDecimal initialDeposit,
        String currency,
        Instant updatedAt
) {

    public static AccountResponse from(Accounts account) {
        return new AccountResponse(
                account.getCashBalance(),
                account.getInitialDeposit(),
                account.getCurrency(),
                account.getUpdatedAt()
        );
    }
}
