package com.sparta.trading.domain.entity;

import java.time.Duration;
import java.time.Instant;

/**
 * MarketClock 조회 경로가 캐싱을 위해 쓰는 불변 스냅샷.
 * MarketClock 엔티티(JPA, protected 생성자)는 Redis 직렬화에 적합하지 않아 따로 둔다.
 * currentSeq/reachedEnd/effectiveStatus 계산 로직은 MarketClock과 동일 — 캐시된 값만으로
 * 재계산 가능해야 하므로(재조회 없이) 여기에도 그대로 둔다.
 */
public record MarketClockSnapshot(
        Long anchorSeq,
        Instant anchorAt,
        Instant anchorMarketTime,
        Long startSeq,
        Long endSeq,
        Integer speedFactor,
        Integer cacheRefreshIntervalMs,
        ClockStatus status,
        Instant updatedAt
) {

    public static MarketClockSnapshot from(MarketClock marketClock) {
        return new MarketClockSnapshot(
                marketClock.getAnchorSeq(),
                marketClock.getAnchorAt(),
                marketClock.getAnchorMarketTime(),
                marketClock.getStartSeq(),
                marketClock.getEndSeq(),
                marketClock.getSpeedFactor(),
                marketClock.getCacheRefreshIntervalMs(),
                marketClock.getStatus(),
                marketClock.getUpdatedAt()
        );
    }

    /** 지금 이 순간의 재생 위치. STOPPED면 앵커 값, RUNNING이면 경과 시간만큼 전진한 값을 end_seq 이내로 반환한다. */
    public long currentSeq(Instant now) {
        if (status != ClockStatus.RUNNING) {
            return anchorSeq;
        }
        long elapsedSeconds = Duration.between(anchorAt, now).getSeconds();
        long computed = anchorSeq + elapsedSeconds * speedFactor;
        return Math.clamp(computed, anchorSeq, endSeq);
    }

    /** 현재 위치가 종료 seq에 도달했는지 반환한다. */
    public boolean reachedEnd(Instant now) {
        return currentSeq(now) >= endSeq;
    }

    /** DB에 저장된 status 대신, 지금 시점 기준으로 실제로 맞는 상태를 계산해 반환한다. */
    public ClockStatus effectiveStatus(Instant now) {
        return reachedEnd(now) ? ClockStatus.STOPPED : status;
    }
}
