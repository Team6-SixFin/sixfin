package com.sparta.trading.application.port;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(
        String symbol,
        BigDecimal price,
        Long seq,
        Instant marketTime,
        ClockStatus clockStatus,
        BigDecimal recent20dHigh,
        BigDecimal recent20dLow,
        BigDecimal recent5dReturn,
        Instant updatedAt,
        Long stockId,
        String stockName
) {
    public Quote(String symbol, BigDecimal price, Long seq, Instant marketTime, ClockStatus clockStatus,
                 BigDecimal recent20dHigh, BigDecimal recent20dLow, BigDecimal recent5dReturn,
                 Instant updatedAt) {
        this(symbol, price, seq, marketTime, clockStatus, recent20dHigh, recent20dLow,
                recent5dReturn, updatedAt, null, null);
    }

    public static Quote from(PriceCandles candle, String symbol, MarketClockSnapshot marketClock, Instant now) {
        return new Quote(
                symbol,
                candle.getClosePrice(),
                candle.getSeq(),
                candle.getMarketTime(),
                marketClock.effectiveStatus(now),
                candle.getRecent20dHigh(),
                candle.getRecent20dLow(),
                candle.getRecent5dReturn(),
                now,
                candle.getStock().getId(),
                candle.getStock().getName()
        );
    }
}
