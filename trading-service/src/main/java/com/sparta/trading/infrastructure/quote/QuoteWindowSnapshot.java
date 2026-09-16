package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record QuoteWindowSnapshot(
        long seq,
        Map<String, Quote> quoteBySymbol
) {
    public static QuoteWindowSnapshot from(long seq, List<PriceCandles> candleList, MarketClockSnapshot marketClock, Instant now){
        Map<String, Quote> quoteMap =
                candleList.stream().map(candle -> Quote.from(candle, candle.getStock().getSymbol(), marketClock, now))
                .collect(Collectors.toMap(
                        Quote::symbol,
                        Function.identity()
                ));
        return new QuoteWindowSnapshot(seq, quoteMap);
    }
}
