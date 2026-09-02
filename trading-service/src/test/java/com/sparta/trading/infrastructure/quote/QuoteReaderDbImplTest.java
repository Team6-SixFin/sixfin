package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteReaderDbImplTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-03T13:30:00Z");

    @Mock
    private StocksRepository stocksRepository;

    @Mock
    private PriceCandlesRepository priceCandlesRepository;

    @Mock
    private CurrentSeqProvider currentSeqProvider;

    @InjectMocks
    private QuoteReaderDbImpl quoteReader;

    private Stocks stockOf(String symbol) {
        return Stocks.builder()
                .symbol(symbol)
                .name(symbol + " Inc.")
                .market("NASDAQ")
                .currency("USD")
                .active(true)
                .build();
    }

    private PriceCandles candleOf(Stocks stock, long seq) {
        return PriceCandles.builder()
                .stock(stock)
                .seq(seq)
                .marketTime(BASE_TIME.plusSeconds(seq * 60))
                .openPrice(BigDecimal.valueOf(100))
                .highPrice(BigDecimal.valueOf(101))
                .lowPrice(BigDecimal.valueOf(99))
                .closePrice(BigDecimal.valueOf(100.5))
                .volume(1000L)
                .recent20dHigh(BigDecimal.valueOf(110))
                .recent20dLow(BigDecimal.valueOf(90))
                .recent5dReturn(BigDecimal.valueOf(1.23))
                .build();
    }

    private MarketClock clockOf(ClockStatus status) {
        return MarketClock.builder()
                .id(1)
                .anchorSeq(100L)
                .anchorAt(BASE_TIME)
                .anchorMarketTime(BASE_TIME)
                .startSeq(0L)
                .endSeq(7393L)
                .speedFactor(1)
                .cacheRefreshIntervalMs(1000)
                .status(status)
                .build();
    }

    private void givenCurrentSeq(MarketClock marketClock, long seq) {
        when(currentSeqProvider.getClock()).thenReturn(marketClock);
        when(currentSeqProvider.currentSeq(marketClock)).thenReturn(seq);
    }

    @Test
    @DisplayName("read: 단일 종목 조회 시 캔들 값이 그대로 매핑된 Quote를 반환한다")
    void read_returnsQuoteMappedFromCandle() {
        Stocks aapl = stockOf("AAPL");
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL"))).thenReturn(List.of(1L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));
        PriceCandles candle = candleOf(aapl, 100L);
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(1L))).thenReturn(List.of(candle));

        Quote quote = quoteReader.read("AAPL");

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.price()).isEqualByComparingTo("100.5");
        assertThat(quote.seq()).isEqualTo(100L);
        assertThat(quote.marketTime()).isEqualTo(candle.getMarketTime());
        assertThat(quote.clockStatus()).isEqualTo(ClockStatus.RUNNING);
        assertThat(quote.recent20dHigh()).isEqualByComparingTo("110");
    }

    @Test
    @DisplayName("readAll: 여러 종목을 조회해도 각 Quote가 올바른 심볼에 매핑된다")
    void readAll_mapsEachQuoteToCorrectSymbol() {
        Stocks aapl = stockOf("AAPL");
        Stocks msft = stockOf("MSFT");
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL", "MSFT"))).thenReturn(List.of(1L, 2L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));
        PriceCandles aaplCandle = candleOf(aapl, 100L);
        PriceCandles msftCandle = candleOf(msft, 100L);
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(1L, 2L)))
                .thenReturn(List.of(aaplCandle, msftCandle));

        List<Quote> quotes = quoteReader.readAll(List.of("AAPL", "MSFT"));

        assertThat(quotes).hasSize(2);
        assertThat(quotes).extracting(Quote::symbol).containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    @DisplayName("readAll: seq 계산은 종목 수와 무관하게 한 번만 호출된다")
    void readAll_computesCurrentSeqOnlyOnce() {
        Stocks aapl = stockOf("AAPL");
        Stocks msft = stockOf("MSFT");
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL", "MSFT"))).thenReturn(List.of(1L, 2L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(1L, 2L)))
                .thenReturn(List.of(candleOf(aapl, 100L), candleOf(msft, 100L)));

        quoteReader.readAll(List.of("AAPL", "MSFT"));

        verify(currentSeqProvider, times(1)).currentSeq(any());
    }

    @Test
    @DisplayName("readAll: 일부 종목만 현재 seq에 데이터가 있으면 나머지는 조용히 빠진다")
    void readAll_silentlyDropsSymbolsMissingAtCurrentSeq() {
        Stocks aapl = stockOf("AAPL");
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL", "NEWCO"))).thenReturn(List.of(1L, 2L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(currentSeqProvider.now()).thenReturn(BASE_TIME.plusSeconds(100));
        // NEWCO는 아직 이 seq에 데이터가 없어서 조회 결과에서 자체적으로 빠진 상황을 가정
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(1L, 2L)))
                .thenReturn(List.of(candleOf(aapl, 100L)));

        List<Quote> quotes = quoteReader.readAll(List.of("AAPL", "NEWCO"));

        assertThat(quotes).hasSize(1);
        assertThat(quotes.get(0).symbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("readAll: 존재하지 않는 심볼이 섞이면 STOCK_NOT_FOUND")
    void readAll_throwsWhenSymbolDoesNotExist() {
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL", "NOPE"))).thenReturn(List.of(1L));

        CustomException exception = assertThrows(CustomException.class,
                () -> quoteReader.readAll(List.of("AAPL", "NOPE")));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("read: 유효한 종목이지만 현재 seq에 캔들이 없으면 PRICE_CANDLE_NOT_FOUND_FOR_SEQ")
    void read_throwsWhenNoCandleAtCurrentSeq() {
        when(stocksRepository.findIdBySymbolIn(List.of("NEWCO"))).thenReturn(List.of(2L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(2L))).thenReturn(List.of());

        CustomException exception = assertThrows(CustomException.class, () -> quoteReader.read("NEWCO"));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
    }

    @Test
    @DisplayName("readAll: 요청한 종목 전부 현재 seq에 데이터가 없으면 PRICE_CANDLE_NOT_FOUND_FOR_SEQ")
    void readAll_throwsWhenNoRequestedSymbolHasDataAtCurrentSeq() {
        when(stocksRepository.findIdBySymbolIn(List.of("A", "B"))).thenReturn(List.of(1L, 2L));
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of(1L, 2L))).thenReturn(List.of());

        CustomException exception = assertThrows(CustomException.class,
                () -> quoteReader.readAll(List.of("A", "B")));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
    }

    @Test
    @DisplayName("readAll: 빈 심볼 리스트로 호출하면 PRICE_CANDLE_NOT_FOUND_FOR_SEQ (조회 결과가 없는 것으로 취급됨)")
    void readAll_throwsWhenSymbolListIsEmpty() {
        when(stocksRepository.findIdBySymbolIn(List.of())).thenReturn(List.of());
        MarketClock marketClock = clockOf(ClockStatus.RUNNING);
        givenCurrentSeq(marketClock, 100L);
        when(priceCandlesRepository.findAllBySeqAndStockIdIn(100L, List.of())).thenReturn(List.of());

        CustomException exception = assertThrows(CustomException.class, () -> quoteReader.readAll(List.of()));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
    }

    @Test
    @DisplayName("readAll: market_clock 행이 없으면 MARKET_CLOCK_NOT_FOUND가 전파된다")
    void readAll_propagatesMarketClockNotFound() {
        when(stocksRepository.findIdBySymbolIn(List.of("AAPL"))).thenReturn(List.of(1L));
        when(currentSeqProvider.getClock())
                .thenThrow(new CustomException(TradingErrorCode.MARKET_CLOCK_NOT_FOUND));

        CustomException exception = assertThrows(CustomException.class,
                () -> quoteReader.readAll(List.of("AAPL")));

        assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.MARKET_CLOCK_NOT_FOUND);
    }
}
