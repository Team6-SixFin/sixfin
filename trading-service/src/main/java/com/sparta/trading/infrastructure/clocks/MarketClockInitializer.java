package com.sparta.trading.infrastructure.clocks;

import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.repository.clocks.MarketClockCommandRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MarketClockInitializer {

    private final MarketClockCommandRepository marketClockCommandRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final Clock clock;

    @Transactional
    public MarketClock initializeClock(MarketClockProperties properties) {
        Instant anchorMarketTime = resolveAnchorMarketTime(properties.startSeq());

        MarketClock marketClock = marketClockCommandRepository.findForUpdate()
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));

        marketClock.reanchor(
                properties.startSeq(),
                clock.instant(),
                anchorMarketTime,
                properties.speedFactor(),
                ClockStatus.STOPPED,
                null);
        return marketClock;
    }

    /** startSeq에 해당하는 캔들의 시각을 찾는다. Seeder의 신규 생성 분기와 로직을 공유한다. */
    public Instant resolveAnchorMarketTime(long seq) {
        return priceCandlesRepository.findFirstBySeq(seq)
                .map(PriceCandles::getMarketTime)
                .orElseThrow(() -> new CustomException(
                        TradingErrorCode.MARKET_CLOCK_SEED_DATA_MISSING,
                        "start-seq(%d)에 해당하는 캔들이 없습니다. CSV 적재를 먼저 실행하세요."
                                .formatted(seq)));
    }
}
