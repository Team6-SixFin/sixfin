package com.sparta.trading.infrastructure.clock;

import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.application.service.MarketClockCommandService;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketClockWarmingSchedulerTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    @Mock
    private CurrentSeqProvider currentSeqProvider;

    @Mock
    private MarketClockCommandService marketClockCommandService;

    @InjectMocks
    private MarketClockWarmingScheduler scheduler;

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
    @DisplayName("RUNNING이고 end_seq에 도달했으면 autoStopIfReached를 호출한다")
    void checkAutoStop_triggersWhenReached() {
        MarketClock marketClock = clockOf(7390L, BASE_TIME, 100, ClockStatus.RUNNING);
        when(currentSeqProvider.getClock()).thenReturn(marketClock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));

        scheduler.checkAutoStop();

        verify(marketClockCommandService, times(1)).autoStopIfReached();
    }

    @Test
    @DisplayName("RUNNING이지만 아직 도달 전이면 호출하지 않는다")
    void checkAutoStop_doesNotTriggerWhenNotReached() {
        MarketClock marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);
        when(currentSeqProvider.getClock()).thenReturn(marketClock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(5));

        scheduler.checkAutoStop();

        verify(marketClockCommandService, never()).autoStopIfReached();
    }

    @Test
    @DisplayName("이미 STOPPED면 now()도 조회하지 않고 바로 반환한다")
    void checkAutoStop_skipsWhenAlreadyStopped() {
        MarketClock marketClock = clockOf(7393L, BASE_TIME, 100, ClockStatus.STOPPED);
        when(currentSeqProvider.getClock()).thenReturn(marketClock);

        scheduler.checkAutoStop();

        verify(marketClockCommandService, never()).autoStopIfReached();
    }
}
