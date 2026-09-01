package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.entity.Stocks;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingStockDetailsFindResponse (
        Long stockId,
        String symbol,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        Long volume,
        BigDecimal recent20DayHigh,
        BigDecimal recent20DayLow,
        BigDecimal recent5DayReturn,
        Instant marketTime,
        Long candleSeq
){
    public static TradingStockDetailsFindResponse from(Long stocksId, String symbol, PriceCandles candles){
        return new TradingStockDetailsFindResponse(
                stocksId,
                symbol,
                // 종가로 현재 가격 설정.
                candles.getClosePrice(),
                candles.getOpenPrice(),
                candles.getHighPrice(),
                candles.getLowPrice(),
                candles.getVolume(),
                candles.getRecent20dHigh(),
                candles.getRecent20dLow(),
                candles.getRecent5dReturn(),
                candles.getMarketTime(),
                candles.getSeq());
    }
}
