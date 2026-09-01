package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Stocks;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingStockFindResponse(
        Long stockId,
        String symbol,
        String name,
        String market,
        String currency,
        BigDecimal currentPrice,
        Instant marketTime,
        Long candleSeq
) {
    public static TradingStockFindResponse from(Stocks stocks, BigDecimal currentPrice, Instant marketTime, Long candleSeq){
        return new TradingStockFindResponse(
                stocks.getId(),
                stocks.getSymbol(),
                stocks.getName(),
                stocks.getMarket(),
                stocks.getCurrency(),
                currentPrice,
                marketTime,
                candleSeq);
    }
}
