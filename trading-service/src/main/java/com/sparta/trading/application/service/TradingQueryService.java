package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.MarketClockResponse;
import com.sparta.trading.domain.entity.MarketClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingQueryService {

    private final CurrentSeqProvider currentSeqProvider;

    public MarketClockResponse getClock() {
        MarketClock marketClock = currentSeqProvider.getClock();
        long currentSeq = currentSeqProvider.currentSeq(marketClock);
        Instant currentMarketTime = currentSeqProvider.marketTimeAt(currentSeq);
        return MarketClockResponse.of(marketClock, currentSeq, currentMarketTime);
    }
}
