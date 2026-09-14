package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.repository.clocks.MarketClockQueryRepository;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** 락 없이 조회만 하는 쪽(GET API, QuoteReader)이 공용으로 쓰는 시계 조회 로직. */
@Component
@RequiredArgsConstructor
public class CurrentSeqProvider {

    private static final int SINGLETON_ID = 1;
    public static final String MARKET_CLOCK_CACHE_NAME = "marketClock";

    private final MarketClockQueryRepository marketClockRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final Clock clock;

    /**
     * 캐싱된 MarketClock 스냅샷 반환. Redis에 없으면 DB에서 읽어 채워넣는다(cache-aside).
     * 무효화는 여기서 하지 않는다 - reanchor() 시점에 {@code @CacheEvict}로 지운다.
     * 행이 초기화(없음) 되지 않으면 {@code MARKET_CLOCK_NOT_FOUND} 반환
     */
    @Cacheable(cacheNames = MARKET_CLOCK_CACHE_NAME, key = "'current'")
    public MarketClockSnapshot getClockSnapshot() {
        MarketClock marketClock = marketClockRepository.findById(SINGLETON_ID)
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));
        return MarketClockSnapshot.from(marketClock);
    }

    /**
     * 현재 가상 시간 번호({@code Seq})조회. Stock 조회를 위해 주로 사용됨.
     * 주로 getClockSnapshot()과 함께 사용.
     */
    public long currentSeq(MarketClockSnapshot marketClock) {
        return marketClock.currentSeq(clock.instant());
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
