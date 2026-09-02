package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;

import java.time.Instant;

/** start/stop/speed/reset 응답 전용. 앵커 값을 그대로 담는다 — 실시간 조회용 응답은 별도로 만든다. */
public record MarketClockInternalResponse(
        Long seq,
        Instant marketTime,
        Integer speedFactor,
        ClockStatus status,
        Instant updatedAt
) {

    public static MarketClockInternalResponse fromAnchor(MarketClock clock) {
        return new MarketClockInternalResponse(
                clock.getAnchorSeq(),
                clock.getAnchorMarketTime(),
                clock.getSpeedFactor(),
                clock.getStatus(),
                clock.getUpdatedAt()
        );
    }
}
