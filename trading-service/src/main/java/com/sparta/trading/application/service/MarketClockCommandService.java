package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.clocks.MarketClockRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * market_clock의 유일한 쓰기 경로. start/stop/speed/reset이 전부 reanchor() 한 곳으로 수렴한다.
 * 조회(currentSeq 계산, GET API)는 별도 Query 서비스에서 다룬다 — 여기는 상태 변경만 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketClockCommandService {

    private final MarketClockRepository marketClockRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final Clock clock;

    @Transactional
    public MarketClock start(UUID userId) {
        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.RUNNING, userId);
        return marketClock;
    }

    @Transactional
    public MarketClock stop(UUID userId) {
        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.STOPPED, userId);
        return marketClock;
    }

    @Transactional
    public MarketClock changeSpeed(int newSpeedFactor, UUID userId) {
        if (newSpeedFactor < 1) {
            throw new CustomException(TradingErrorCode.MARKET_CLOCK_INVALID_SPEED);
        }

        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        long seq = marketClock.currentSeq(now);
        Instant marketTime = marketTimeAt(seq);
        marketClock.reanchor(seq, now, marketTime, newSpeedFactor, marketClock.getStatus(), userId);
        return marketClock;
    }

    @Transactional
    public MarketClock reset(long targetSeq, UUID userId) {
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
        Instant marketTime = marketTimeAt(targetSeq);
        marketClock.reanchor(targetSeq, now, marketTime, marketClock.getSpeedFactor(), ClockStatus.STOPPED, userId);
        return marketClock;
    }

    /**
     * end_seq 도달을 감지해 RUNNING -> STOPPED로 전이한다. 락을 잡은 뒤 다시 확인하므로
     * 이미 STOPPED거나 아직 도달 전이면 아무 것도 하지 않는다 (멱등, 중복 호출돼도 무해).
     * 호출자의 트랜잭션(특히 readOnly)과 무관하게 항상 별도 트랜잭션으로 실행된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoStopIfReached() {
        MarketClock marketClock = getClockForUpdate();
        Instant now = clock.instant();
        if (marketClock.getStatus() != ClockStatus.RUNNING || !marketClock.reachedEnd(now)) {
            return;
        }

        Instant marketTime = marketTimeAt(marketClock.getEndSeq());
        marketClock.reanchor(marketClock.getEndSeq(), now, marketTime, marketClock.getSpeedFactor(), ClockStatus.STOPPED, null);
    }

    private Instant marketTimeAt(long seq) {
        return priceCandlesRepository.findFirstBySeq(seq)
                .map(PriceCandles::getMarketTime)
                .orElseThrow(() -> new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

    private MarketClock getClockForUpdate() {
        return marketClockRepository.findForUpdate()
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));
    }

}
