package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.clocks.MarketClockRepository;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * GET API, QuoteReader가 공용으로 쓰는 시계 조회 로직.
 * end_seq 도달을 감지하면 AUTO_STOP 전이를 트리거한다({@link MarketClockCommandService#autoStopIfReached()}).
 */
@Component
@RequiredArgsConstructor
public class CurrentSeqProvider {

    private static final int SINGLETON_ID = 1;

    private final MarketClockRepository marketClockRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final MarketClockCommandService marketClockCommandService;
    private final Clock clock;

    /**
     * 단순 MarketClock객체 반환
     * 행이 초기화(없음) 되지 않으면 {@code MARKET_CLOCK_NOT_FOUND} 반환
     */
    public MarketClock getClock() {
        return marketClockRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));
    }

    /**
     * 현재 가상 시간 번호({@code Seq})조회. Stock 조회를 위해 주로 사용됨.
     * 주로 getClock()과 함께 사용.
     */
    public long currentSeq(MarketClock marketClock) {
        long seq = marketClock.currentSeq(clock.instant());
        if (marketClock.getStatus() == ClockStatus.RUNNING && seq >= marketClock.getEndSeq()) {
            marketClockCommandService.autoStopIfReached();
        }
        return seq;
    }

    /**
     * 현재 가상 시장 시간 ({@code Instant})조회.
     * 주로 currentSeq()과 함께 사용.
     * 데이터에 적재되지 않은 Seq에 대해 {@code PRICE_CANDLE_NOT_FOUND_FOR_SEQ} 반환
     */
    public Instant marketTimeAt(long seq) {
        return priceCandlesRepository.findFirstBySeq(seq)
                .map(PriceCandles::getMarketTime)
                .orElseThrow(() -> new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

    public Instant now() {
        return clock.instant();
    }
}
