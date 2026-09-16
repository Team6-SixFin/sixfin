package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * cache-aside 경로(히트/미스/부분미스/Redis 장애) 전부 결국 올바른 결과로 수렴하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class QuoteReaderRedisImplTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    @Mock
    private RedisTemplate<String, QuoteWindowSnapshot> quoteRedisTemplate;

    @Mock
    private ValueOperations<String, QuoteWindowSnapshot> valueOperations;

    @Mock
    private QuoteReaderDbImpl dbFallback;

    @Mock
    private CurrentSeqProvider currentSeqProvider;

    private QuoteReaderRedisImpl quoteReader;

    @BeforeEach
    void setUp() {
        quoteReader = new QuoteReaderRedisImpl(quoteRedisTemplate, dbFallback, currentSeqProvider);
        when(currentSeqProvider.getClockSnapshot()).thenReturn(clockOf());
        when(currentSeqProvider.currentSeq(any())).thenReturn(100L);
        when(quoteRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private MarketClockSnapshot clockOf() {
        return new MarketClockSnapshot(
                100L, BASE_TIME, BASE_TIME, 0L, 7393L, 1, 1000, ClockStatus.RUNNING, Instant.now());
    }

    private Quote quoteOf(String symbol) {
        return new Quote(symbol, BigDecimal.valueOf(100.5), 100L, BASE_TIME, ClockStatus.RUNNING,
                BigDecimal.valueOf(110), BigDecimal.valueOf(90), BigDecimal.valueOf(1.23), BASE_TIME);
    }

    @Test
    @DisplayName("요청 종목이 캐시에 전부 있으면 DB를 타지 않고 캐시 값을 반환한다")
    void returnsFromCacheOnFullHit() {
        Quote aaplQuote = quoteOf("AAPL");
        when(valueOperations.get("price:100")).thenReturn(new QuoteWindowSnapshot(100L, Map.of("AAPL", aaplQuote)));

        List<Quote> result = quoteReader.readAll(List.of("AAPL"));

        assertThat(result).containsExactly(aaplQuote);
        verifyNoInteractions(dbFallback);
    }

    @Test
    @DisplayName("캐시 미스(키 없음)면 DB로 폴백한다")
    void fallsBackToDbOnCacheMiss() {
        when(valueOperations.get("price:100")).thenReturn(null);
        List<Quote> dbResult = List.of(quoteOf("AAPL"));
        when(dbFallback.readAll(List.of("AAPL"))).thenReturn(dbResult);

        List<Quote> result = quoteReader.readAll(List.of("AAPL"));

        assertThat(result).isEqualTo(dbResult);
    }

    @Test
    @DisplayName("요청 종목 중 일부만 캐시에 없어도 전체를 DB로 폴백한다(캐시·DB 결과를 섞지 않음)")
    void fallsBackToDbOnPartialMiss() {
        when(valueOperations.get("price:100"))
                .thenReturn(new QuoteWindowSnapshot(100L, Map.of("AAPL", quoteOf("AAPL"))));
        List<Quote> dbResult = List.of(quoteOf("AAPL"), quoteOf("MSFT"));
        when(dbFallback.readAll(List.of("AAPL", "MSFT"))).thenReturn(dbResult);

        List<Quote> result = quoteReader.readAll(List.of("AAPL", "MSFT"));

        assertThat(result).isEqualTo(dbResult);
    }

    @Test
    @DisplayName("Redis 조회 자체가 예외를 던져도 DB로 폴백해 정상 응답한다")
    void fallsBackToDbWhenRedisThrows() {
        when(valueOperations.get("price:100")).thenThrow(new RuntimeException("connection refused"));
        List<Quote> dbResult = List.of(quoteOf("AAPL"));
        when(dbFallback.readAll(List.of("AAPL"))).thenReturn(dbResult);

        List<Quote> result = quoteReader.readAll(List.of("AAPL"));

        assertThat(result).isEqualTo(dbResult);
    }

    @Test
    @DisplayName("read()는 readAll()을 거쳐 캐시 히트 시 바로 반환된다")
    void readDelegatesToReadAllOnCacheHit() {
        Quote aaplQuote = quoteOf("AAPL");
        when(valueOperations.get("price:100")).thenReturn(new QuoteWindowSnapshot(100L, Map.of("AAPL", aaplQuote)));

        Quote result = quoteReader.read("AAPL");

        assertThat(result).isEqualTo(aaplQuote);
        verifyNoInteractions(dbFallback);
    }
}
