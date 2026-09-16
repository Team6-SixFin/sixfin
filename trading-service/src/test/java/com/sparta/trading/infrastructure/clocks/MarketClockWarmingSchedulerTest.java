package com.sparta.trading.infrastructure.clocks;

import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.application.service.MarketClocksCommandService;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
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
    private MarketClocksCommandService marketClockCommandService;

    @InjectMocks
    private MarketClockWarmingScheduler scheduler;

    private MarketClockSnapshot clockOf(long anchorSeq, Instant anchorAt, int speedFactor, ClockStatus status) {
        return new MarketClockSnapshot(
                anchorSeq,
                anchorAt,
                BASE_TIME.plusSeconds(anchorSeq * 60),
                0L,
                7393L,
                speedFactor,
                1000,
                status,
                Instant.now()
        );
    }

    @Test
    @DisplayName("RUNNING이고 end_seq에 도달했으면 autoStopIfReached를 호출한다")
    void checkAutoStop_triggersWhenReached() {
        MarketClockSnapshot marketClock = clockOf(7390L, BASE_TIME, 100, ClockStatus.RUNNING);
        when(currentSeqProvider.getClockSnapshot()).thenReturn(marketClock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));

        scheduler.checkAutoStop();

        verify(marketClockCommandService, times(1)).autoStopIfReached();
    }

    @Test
    @DisplayName("RUNNING이지만 아직 도달 전이면 호출하지 않는다")
    void checkAutoStop_doesNotTriggerWhenNotReached() {
        MarketClockSnapshot marketClock = clockOf(0L, BASE_TIME, 1, ClockStatus.RUNNING);
        when(currentSeqProvider.getClockSnapshot()).thenReturn(marketClock);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(5));

        scheduler.checkAutoStop();

        verify(marketClockCommandService, never()).autoStopIfReached();
    }

    @Test
    @DisplayName("이미 STOPPED면 now()도 조회하지 않고 바로 반환한다")
    void checkAutoStop_skipsWhenAlreadyStopped() {
        MarketClockSnapshot marketClock = clockOf(7393L, BASE_TIME, 100, ClockStatus.STOPPED);
        when(currentSeqProvider.getClockSnapshot()).thenReturn(marketClock);

        scheduler.checkAutoStop();

        verify(marketClockCommandService, never()).autoStopIfReached();
    }
}
