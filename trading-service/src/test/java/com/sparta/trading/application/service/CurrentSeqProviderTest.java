package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.clocks.MarketClockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentSeqProviderTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    @Mock
    private MarketClockRepository marketClockRepository;

    @Mock
    private PriceCandlesRepository priceCandlesRepository;

    @Mock
    private MarketClockCommandService marketClockCommandService;

    @Mock
    private Clock clock;

    @InjectMocks
    private CurrentSeqProvider currentSeqProvider;

    private MarketClock clockOf(long anchorSeq, Instant anchorAt, int speedFactor, ClockStatus status) {
        return MarketClock.builder()
                .id(1)
                .anchorSeq(anchorSeq)
                .anchorAt(anchorAt)
                .anchorMarketTime(BASE_TIME.plusSeconds(anchorSeq * 60))
                .startSeq(0L)
                .endSeq(7393L)
                .speedFactor(speedFactor)
                .cacheRefreshIntervalMs(1000)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("아직 end_seq 미도달이면 autoStop을 트리거하지 않는다")
    void currentSeq_doesNotTriggerAutoStopWhenNotReached() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);
        when(clock.instant()).thenReturn(BASE_TIME.plusSeconds(5));

        long seq = currentSeqProvider.currentSeq(marketClock);

        assertThat(seq).isEqualTo(5L);
        verify(marketClockCommandService, never()).autoStopIfReached();
    }

    @Test
    @DisplayName("RUNNING 중 end_seq에 도달하면 autoStop을 트리거한다")
    void currentSeq_triggersAutoStopWhenReached() {
        MarketClock marketClock = clockOf(7390L, BASE_TIME, 100, ClockStatus.RUNNING);
        when(clock.instant()).thenReturn(BASE_TIME.plusSeconds(100));

        long seq = currentSeqProvider.currentSeq(marketClock);

        assertThat(seq).isEqualTo(7393L);
        verify(marketClockCommandService, times(1)).autoStopIfReached();
    }

    @Test
    @DisplayName("이미 STOPPED면 도달 여부와 무관하게 autoStop을 트리거하지 않는다")
    void currentSeq_doesNotTriggerAutoStopWhenAlreadyStopped() {
        MarketClock marketClock = clockOf(7393L, BASE_TIME, 100, ClockStatus.STOPPED);

        long seq = currentSeqProvider.currentSeq(marketClock);

        assertThat(seq).isEqualTo(7393L);
        verify(marketClockCommandService, never()).autoStopIfReached();
    }
}
