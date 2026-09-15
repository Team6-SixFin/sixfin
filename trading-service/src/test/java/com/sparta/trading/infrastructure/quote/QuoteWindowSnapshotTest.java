package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.entity.Stocks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteWindowSnapshotTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    private Stocks stockOf(String symbol) {
        return Stocks.builder()
                .symbol(symbol)
                .name(symbol + " Inc.")
                .market("NASDAQ")
                .currency("USD")
                .active(true)
                .build();
    }

    private PriceCandles candleOf(Stocks stock, long seq) {
        return PriceCandles.builder()
                .stock(stock)
                .seq(seq)
                .marketTime(BASE_TIME.plusSeconds(seq * 60))
                .openPrice(BigDecimal.valueOf(100))
                .highPrice(BigDecimal.valueOf(101))
                .lowPrice(BigDecimal.valueOf(99))
                .closePrice(BigDecimal.valueOf(100.5))
                .volume(1000L)
                .recent20dHigh(BigDecimal.valueOf(110))
                .recent20dLow(BigDecimal.valueOf(90))
                .recent5dReturn(BigDecimal.valueOf(1.23))
                .build();
    }

    @Test
    void fromGroupsCandlesBySymbolAndCarriesSeq() {
        Stocks aapl = stockOf("AAPL");
        Stocks msft = stockOf("MSFT");
        MarketClockSnapshot clock = new MarketClockSnapshot(
                100L, BASE_TIME, BASE_TIME, 0L, 7393L, 1, 1000, ClockStatus.RUNNING, Instant.now());

        QuoteWindowSnapshot snapshot = QuoteWindowSnapshot.from(
                100L, List.of(candleOf(aapl, 100L), candleOf(msft, 100L)), clock, BASE_TIME);

        assertThat(snapshot.seq()).isEqualTo(100L);
        assertThat(snapshot.quoteBySymbol()).containsOnlyKeys("AAPL", "MSFT");
        assertThat(snapshot.quoteBySymbol().get("AAPL").seq()).isEqualTo(100L);
        assertThat(snapshot.quoteBySymbol().get("AAPL").price()).isEqualByComparingTo("100.5");
        assertThat(snapshot.quoteBySymbol().get("AAPL").clockStatus()).isEqualTo(ClockStatus.RUNNING);
    }
}
