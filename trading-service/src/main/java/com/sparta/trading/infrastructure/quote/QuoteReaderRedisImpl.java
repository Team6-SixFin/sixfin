package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis hot window 캐시에서 먼저 읽고(cache-aside), 캐시 미스나 Redis 장애 시 DB로 폴백하는 QuoteReader.
 * 캐시를 채우는 건 {@link QuoteHotWindowWarmingScheduler}가 전담한다 — 여기는 읽기만 하고 쓰지 않는다.
 * {@code quote.reader.type=redis}일 때만 뜨고, {@code @Primary}라 그때는 {@link QuoteReaderDbImpl} 대신
 * 이 빈이 주입된다. 값을 안 주면(기본) DB 구현체만 남아 기존과 동일하게 동작한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "quote.reader", name = "type", havingValue = "redis")
@Primary
@RequiredArgsConstructor
public class QuoteReaderRedisImpl implements QuoteReader {

    private final RedisTemplate<String, QuoteWindowSnapshot> quoteRedisTemplate;
    private final QuoteReaderDbImpl dbFallback;
    private final CurrentSeqProvider currentSeqProvider;

    @Override
    public Quote read(String symbol) {
        return readAll(List.of(symbol)).get(0);
    }

    @Override
    public List<Quote> readAll(List<String> symbolList) {
        MarketClockSnapshot clock = currentSeqProvider.getClockSnapshot();
        long seq = currentSeqProvider.currentSeq(clock);

        QuoteWindowSnapshot cached = getFromCacheSafely(seq);
        if (cached == null) {
            return dbFallback.readAll(symbolList);
        }

        List<Quote> quotes = new ArrayList<>(symbolList.size());
        for (String symbol : symbolList) {
            Quote quote = cached.quoteBySymbol().get(symbol);

            // 창 범위 안이면 있어야 할 종목이 캐시에 없다 — 일부만 캐시/DB를 섞지 않고
            // 이 요청 전체를 DB로 폴백해 한 응답 안에서 데이터 출처가 갈리는 걸 막는다.
            if (quote == null) {
                return dbFallback.readAll(symbolList);
            }
            quotes.add(quote);
        }
        return quotes;
    }

    private QuoteWindowSnapshot getFromCacheSafely(long seq) {
        try {
            return quoteRedisTemplate.opsForValue().get("price:%d".formatted(seq));
        } catch (Exception e) {
            log.warn("[Redis Quote Cache] price:{} 조회 실패, DB로 폴백합니다.", seq, e);
            return null;
        }
    }
}
