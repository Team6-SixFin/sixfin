package com.sparta.trading.application.port;

import com.sparta.trading.domain.entity.ClockStatus;

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
}
