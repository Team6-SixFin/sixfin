package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteHotWindowWarmingSchedulerTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");
    private static final Duration TTL = Duration.ofMillis(3000L);

    @Mock
    private PriceCandlesRepository priceCandlesRepository;

    @Mock
    private CurrentSeqProvider currentSeqProvider;

    @Mock
    private RedisTemplate<String, QuoteWindowSnapshot> quoteRedisTemplate;

    private QuoteHotWindowWarmingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new QuoteHotWindowWarmingScheduler(priceCandlesRepository, currentSeqProvider, quoteRedisTemplate);
    }

    private MarketClockSnapshot clockOf(long anchorSeq, int speedFactor, ClockStatus status) {
        return new MarketClockSnapshot(
                anchorSeq, BASE_TIME, BASE_TIME, 0L, 7393L, speedFactor, 1000, status, Instant.now());
    }

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
    @DisplayName("시계가 STOPPED면 조회·캐싱 둘 다 하지 않는다")
    void doesNothingWhenClockStopped() {
        when(currentSeqProvider.getClockSnapshot()).thenReturn(clockOf(100L, 1, ClockStatus.STOPPED));

        scheduler.cacheQuoteData();

        verifyNoInteractions(priceCandlesRepository);
        verifyNoInteractions(quoteRedisTemplate);
    }

    @Test
    @DisplayName("RUNNING이면 speedFactor만큼의 창을 seq별 개별 키로 캐싱한다")
    void cachesEachSeqInWindowAsSeparateKeyWhenRunning() {
        MarketClockSnapshot clock = clockOf(100L, 3, ClockStatus.RUNNING); // window: 100~103
        when(currentSeqProvider.getClockSnapshot()).thenReturn(clock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME);

        Stocks aapl = stockOf("AAPL");
        when(priceCandlesRepository.findAllBySeqBetween(100L, 103L)).thenReturn(List.of(
                candleOf(aapl, 100L), candleOf(aapl, 101L), candleOf(aapl, 102L), candleOf(aapl, 103L)));

        ValueOperations<String, QuoteWindowSnapshot> valueOperations = mock(ValueOperations.class);
        when(quoteRedisTemplate.opsForValue()).thenReturn(valueOperations);

        scheduler.cacheQuoteData();

        verify(valueOperations).set(eq("price:100"), any(QuoteWindowSnapshot.class), eq(TTL));
        verify(valueOperations).set(eq("price:101"), any(QuoteWindowSnapshot.class), eq(TTL));
        verify(valueOperations).set(eq("price:102"), any(QuoteWindowSnapshot.class), eq(TTL));
        verify(valueOperations).set(eq("price:103"), any(QuoteWindowSnapshot.class), eq(TTL));
    }

    @Test
    @DisplayName("특정 seq에 캔들이 없으면 그 seq만 건너뛴다")
    void skipsSeqWithNoCandles() {
        MarketClockSnapshot clock = clockOf(100L, 1, ClockStatus.RUNNING); // window: 100~101
        when(currentSeqProvider.getClockSnapshot()).thenReturn(clock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME);

        Stocks aapl = stockOf("AAPL");
        // seq 101에는 데이터가 없는 상황을 가정
        when(priceCandlesRepository.findAllBySeqBetween(100L, 101L)).thenReturn(List.of(candleOf(aapl, 100L)));

        ValueOperations<String, QuoteWindowSnapshot> valueOperations = mock(ValueOperations.class);
        when(quoteRedisTemplate.opsForValue()).thenReturn(valueOperations);

        scheduler.cacheQuoteData();

        verify(valueOperations, times(1)).set(anyString(), any(QuoteWindowSnapshot.class), eq(TTL));
        verify(valueOperations).set(eq("price:100"), any(QuoteWindowSnapshot.class), eq(TTL));
    }
}
