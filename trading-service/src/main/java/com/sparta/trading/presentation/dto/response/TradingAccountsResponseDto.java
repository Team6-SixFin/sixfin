package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradingAccountsResponseDto(UUID id,
                                         UUID userId,
                                         BigDecimal cashBalance,
                                         BigDecimal initialDeposit,
                                         String currency,
                                         Instant createdAt) {
    public static TradingAccountsResponseDto from(Accounts accounts){
        return new TradingAccountsResponseDto(accounts.getId(),
                accounts.getUserId(),
                accounts.getCashBalance(),
                accounts.getInitialDeposit(),
                accounts.getCurrency(),
                accounts.getCreatedAt());
    }
}
