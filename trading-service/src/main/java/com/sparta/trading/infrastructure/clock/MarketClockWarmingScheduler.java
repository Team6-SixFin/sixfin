package com.sparta.trading.infrastructure.clock;

import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.application.service.MarketClockCommandService;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * end_seq 도달을 주기적으로 감지해 시계를 STOPPED로 전이시킨다.
 * 응답 정확성은
 */
@Component
@RequiredArgsConstructor
public class MarketClockWarmingScheduler {

    private final CurrentSeqProvider currentSeqProvider;
    private final MarketClockCommandService marketClockCommandService;

    @Scheduled(fixedDelayString = "${market.clock.cache-refresh-interval-ms}")
    public void checkAutoStop() {
        MarketClock marketClock = currentSeqProvider.getClock();
        if (marketClock.getStatus() != ClockStatus.RUNNING) {
            return;
        }

        Instant now = currentSeqProvider.now();
        if (marketClock.reachedEnd(now)) {
            marketClockCommandService.autoStopIfReached();
        }
    }
}
