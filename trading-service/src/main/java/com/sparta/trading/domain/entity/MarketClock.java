package com.sparta.trading.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 전 종목이 공유하는 단일 행 가상 시계. currentSeq()로 재생 위치를 계산하고, reanchor()로 상태를 갱신한다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_market_clock")
public class MarketClock {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "anchor_seq", nullable = false)
    private Long anchorSeq;

    @Column(name = "anchor_at", nullable = false)
    private Instant anchorAt;

    @Column(name = "anchor_market_time", nullable = false)
    private Instant anchorMarketTime;

    @Column(name = "start_seq", nullable = false)
    private Long startSeq;

    @Column(name = "end_seq", nullable = false)
    private Long endSeq;

    @Column(name = "speed_factor", nullable = false)
    private Integer speedFactor;

    @Column(name = "cache_refresh_interval_ms", nullable = false)
    private Integer cacheRefreshIntervalMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClockStatus status;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 마지막으로 상태를 바꾼 사용자. null이면 시스템에 의한 자동 전이. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Builder
    private MarketClock(Integer id, Long anchorSeq, Instant anchorAt, Instant anchorMarketTime,
                         Long startSeq, Long endSeq, Integer speedFactor, Integer cacheRefreshIntervalMs,
                         ClockStatus status) {
        this.id = id;
        this.anchorSeq = anchorSeq;
        this.anchorAt = anchorAt;
        this.anchorMarketTime = anchorMarketTime;
        this.startSeq = startSeq;
        this.endSeq = endSeq;
        this.speedFactor = speedFactor;
        this.cacheRefreshIntervalMs = cacheRefreshIntervalMs;
        this.status = status;
    }

    /**
     * 앵커 상태를 갱신한다. newAnchorSeq는 호출자가 계산해서 전달한다.
     * 캐시 무효화는 여기가 아니라 {@code MarketClocksCommandService}의 각 쓰기 메서드에서
     * {@code @CacheEvict}로 한다 — 이 엔티티는 스프링 빈이 아니라 AOP 프록시가 안 걸려서
     * 여기 붙여봐야 동작하지 않는다.
     */
    public void reanchor(long newAnchorSeq, Instant now, Instant newAnchorMarketTime,
                          int newSpeedFactor, ClockStatus newStatus, UUID updatedBy) {
        this.anchorSeq = Math.clamp(newAnchorSeq, startSeq, endSeq);
        this.anchorAt = now;
        this.anchorMarketTime = newAnchorMarketTime;
        this.speedFactor = newSpeedFactor;
        this.status = newStatus;
        this.updatedBy = updatedBy;
    }

    /**
     * 지금 이 순간의 재생 위치. STOPPED면 앵커 값, RUNNING이면 경과 시간만큼 전진한 값을 end_seq 이내로 반환한다.
     * 이 계산 로직은 {@link MarketClockSnapshot}에도 동일하게 있다 — 캐시된 스냅샷만으로 재계산 가능해야 해서
     * 의도적으로 중복해뒀다. 이 메서드를 고치면 그쪽도 같이 고칠 것.
     */
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
