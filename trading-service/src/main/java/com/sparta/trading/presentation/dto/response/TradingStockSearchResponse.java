package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Stocks;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingStockSearchResponse (
        Long stockId,
        String symbol,
        String name,
        String market,
        String currency,
        BigDecimal currentPrice,
        Instant marketTime
) {
    public static TradingStockSearchResponse from(Stocks stocks, BigDecimal currentPrice, Instant marketTime){
        return new TradingStockSearchResponse(
                stocks.getId(),
                stocks.getSymbol(),
                stocks.getName(),
                stocks.getMarket(),
                stocks.getCurrency(),
                currentPrice,
                marketTime);
    }
}
