package com.sparta.trading.infrastructure.clocks;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.repository.clocks.MarketClockCommandRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 앱 재기동 시 기존 market_clock 행을 seq=startSeq/STOPPED로 되돌리는 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MarketClockInitializerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final Instant ANCHOR_MARKET_TIME = Instant.parse("2024-01-02T00:00:00Z");
    private static final MarketClockProperties PROPERTIES =
            new MarketClockProperties(0L, 7393L, 1, 1000);

    @Mock
    private MarketClockCommandRepository marketClockCommandRepository;

    @Mock
    private PriceCandlesRepository priceCandlesRepository;

    private MarketClockInitializer initializer;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        initializer = new MarketClockInitializer(marketClockCommandRepository, priceCandlesRepository, fixedClock);
    }

    private MarketClock runningClockMidway() {
        return MarketClock.builder()
                .id(1)
                .anchorSeq(5000L)
                .anchorAt(Instant.parse("2026-01-01T00:00:00Z"))
                .anchorMarketTime(Instant.parse("2024-06-01T00:00:00Z"))
                .startSeq(PROPERTIES.startSeq())
                .endSeq(PROPERTIES.endSeq())
                .speedFactor(5)
                .cacheRefreshIntervalMs(PROPERTIES.cacheRefreshIntervalMs())
                .status(ClockStatus.RUNNING)
                .build();
    }

    @Test
    void resetsExistingClockToStartSeqAndStopped() {
        MarketClock clock = runningClockMidway();
        when(marketClockCommandRepository.findForUpdate()).thenReturn(Optional.of(clock));
        PriceCandles candle = mock(PriceCandles.class);
        when(candle.getMarketTime()).thenReturn(ANCHOR_MARKET_TIME);
        when(priceCandlesRepository.findFirstBySeq(PROPERTIES.startSeq())).thenReturn(Optional.of(candle));

        MarketClock result = initializer.initializeClock(PROPERTIES);

        assertThat(result.getAnchorSeq()).isEqualTo(PROPERTIES.startSeq());
        assertThat(result.getAnchorAt()).isEqualTo(FIXED_NOW);
        assertThat(result.getAnchorMarketTime()).isEqualTo(ANCHOR_MARKET_TIME);
        assertThat(result.getSpeedFactor()).isEqualTo(PROPERTIES.speedFactor());
        assertThat(result.getStatus()).isEqualTo(ClockStatus.STOPPED);
        assertThat(result.getUpdatedBy()).isNull();
    }

    @Test
    void throwsWhenClockRowDoesNotExist() {
        PriceCandles candle = mock(PriceCandles.class);
        when(candle.getMarketTime()).thenReturn(ANCHOR_MARKET_TIME);
        when(priceCandlesRepository.findFirstBySeq(PROPERTIES.startSeq())).thenReturn(Optional.of(candle));
        when(marketClockCommandRepository.findForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer.initializeClock(PROPERTIES))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.MARKET_CLOCK_NOT_FOUND);
    }

    @Test
    void throwsWhenNoCandleExistsForStartSeq() {
        when(priceCandlesRepository.findFirstBySeq(PROPERTIES.startSeq())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> initializer.initializeClock(PROPERTIES))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(TradingErrorCode.MARKET_CLOCK_SEED_DATA_MISSING);
    }

    @Test
    void resolveAnchorMarketTimeReturnsCandleMarketTime() {
        PriceCandles candle = mock(PriceCandles.class);
        when(candle.getMarketTime()).thenReturn(ANCHOR_MARKET_TIME);
        when(priceCandlesRepository.findFirstBySeq(0L)).thenReturn(Optional.of(candle));

        Instant result = initializer.resolveAnchorMarketTime(0L);

        assertThat(result).isEqualTo(ANCHOR_MARKET_TIME);
    }
}
