package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.MarketClockResponse;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.repository.MarketClockRepository;
import com.sparta.trading.domain.repository.PriceCandlesRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingQueryService {

    private final MarketClockRepository marketClockRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final Clock clock;

    public MarketClockResponse getClock() {
        MarketClock marketClock = getClockForQuery();
        long currentSeq = marketClock.currentSeq(clock.instant());
        Instant currentMarketTime = marketTimeAt(currentSeq);
        return MarketClockResponse.of(marketClock, currentSeq, currentMarketTime);
    }

    private MarketClock getClockForQuery() {
        return marketClockRepository.findById(1)
                .orElseThrow(() -> new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));
    }

    private Instant marketTimeAt(long seq) {
        return priceCandlesRepository.findFirstBySeq(seq)
                .map(PriceCandles::getMarketTime)
                .orElseThrow(() -> new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

}
