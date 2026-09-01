package com.sparta.trading.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_daily_candles",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_candle", columnNames = {"stock_id", "trade_date"})
)
public class DailyCandles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "recent_20d_high", precision = 19, scale = 4)
    private BigDecimal recent20dHigh;

    @Column(name = "recent_20d_low", precision = 19, scale = 4)
    private BigDecimal recent20dLow;

    @Column(name = "recent_5d_return", precision = 7, scale = 2)
    private BigDecimal recent5dReturn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Builder
    private DailyCandles(Long stockId, LocalDate tradeDate, BigDecimal openPrice, BigDecimal highPrice,
                          BigDecimal lowPrice, BigDecimal closePrice, Long volume,
                          BigDecimal recent20dHigh, BigDecimal recent20dLow, BigDecimal recent5dReturn) {
        this.stockId = stockId;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.recent20dHigh = recent20dHigh;
        this.recent20dLow = recent20dLow;
        this.recent5dReturn = recent5dReturn;
    }
}
