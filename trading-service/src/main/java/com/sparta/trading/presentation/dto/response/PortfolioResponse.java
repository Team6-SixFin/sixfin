package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Accounts;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioResponse(
        BigDecimal cashBalance,
        BigDecimal stockValuation,
        BigDecimal totalAssets,
        BigDecimal unrealizedProfit,
        BigDecimal unrealizedReturnRate,
        Instant marketTime
) {
    public static PortfolioResponse of(
            Accounts account,
            BigDecimal stockValuation,
            BigDecimal unrealizedProfit,
            BigDecimal unrealizedReturnRate,
            Instant marketTime
    ) {
        BigDecimal cashBalance = account.getCashBalance();
        BigDecimal totalAssets = cashBalance.add(stockValuation);

        return new PortfolioResponse(
                cashBalance,
                stockValuation,
                totalAssets,
                unrealizedProfit,
                unrealizedReturnRate,
                marketTime
        );
    }

}
