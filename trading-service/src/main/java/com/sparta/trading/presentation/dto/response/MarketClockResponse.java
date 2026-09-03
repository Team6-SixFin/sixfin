package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;

import java.time.Instant;

public record MarketClockResponse (
        Long currentSeq,
        Instant currentMarketTime,
        Long startSeq,
        Long endSeq,
        Integer speedFactor,
        ClockStatus status,
        Instant updatedAt
) {
    public static MarketClockResponse of(MarketClock clock, Long currentSeq, Instant currentMarketTime, ClockStatus effectiveStatus) {
        return new MarketClockResponse(
                currentSeq,
                currentMarketTime,
                clock.getStartSeq(),
                clock.getEndSeq(),
                clock.getSpeedFactor(),
                effectiveStatus,
                clock.getUpdatedAt()
        );
    }
}
