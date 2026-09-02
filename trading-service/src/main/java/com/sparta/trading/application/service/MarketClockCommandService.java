package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.repository.MarketClockRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * market_clock의 유일한 쓰기 경로. start/stop/speed/reset이 전부 reanchor() 한 곳으로 수렴한다.
 * 조회(currentSeq 계산, GET API)는 별도 Query 서비스에서 다룬다 — 여기는 상태 변경만 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketClockCommandService {

    private final MarketClockRepository marketClockRepository;
    private final CurrentSeqProvider currentSeqProvider;
    private final Clock clock;

    @Transactional
    public MarketClock start() {
        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = currentSeqProvider.marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.RUNNING);
        return marketClock;
    }

    @Transactional
    public MarketClock stop() {
        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = currentSeqProvider.marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.STOPPED);
        return marketClock;
    }

    @Transactional
    public MarketClock changeSpeed(int newSpeedFactor) {
        if (newSpeedFactor < 1) {
            throw new CustomException(TradingErrorCode.MARKET_CLOCK_INVALID_SPEED);
        }

        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = currentSeqProvider.marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, newSpeedFactor, marketClock.getStatus());
        return marketClock;
    }

    @Transactional
    public MarketClock reset(long targetSeq) {
        MarketClock marketClock = getClockForUpdate();

        if (marketClock.getStatus() != ClockStatus.STOPPED) {
            throw new CustomException(TradingErrorCode.MARKET_CLOCK_RUNNING);
        }
        if (targetSeq < marketClock.getStartSeq() || targetSeq > marketClock.getEndSeq()) {
            throw new CustomException(TradingErrorCode.MARKET_CLOCK_SEQ_OUT_OF_RANGE);
        }
        if (targetSeq < marketClock.getAnchorSeq()) {
            log.warn("[market-clock] seq {} -> {} 로 되감기. 주문/포지션은 초기화되지 않음",
                    marketClock.getAnchorSeq(), targetSeq);
        }

        Instant now = clock.instant();
        Instant marketTime = currentSeqProvider.marketTimeAt(targetSeq);
        marketClock.reanchor(targetSeq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.STOPPED);
        return marketClock;
    }

    private MarketClock getClockForUpdate() {
        return marketClockRepository.findForUpdate()
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));
    }

}
