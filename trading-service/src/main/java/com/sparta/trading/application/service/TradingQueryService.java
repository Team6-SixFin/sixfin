package com.sparta.trading.application.service;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.MarketClockResponse;
import com.sparta.trading.presentation.dto.response.TradingStockDetailsFindResponse;
import com.sparta.trading.presentation.dto.response.TradingStockFindResponse;
import com.sparta.trading.presentation.dto.response.TradingStockSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingQueryService {

    private final CurrentSeqProvider currentSeqProvider;
    private final StocksRepository stocksRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final QuoteReader quoteReader;

    // ==============================
    // = 시세
    // ==============================
    public MarketClockResponse getClock() {
        MarketClock marketClock = currentSeqProvider.getClock();
        long currentSeq = currentSeqProvider.currentSeq(marketClock);
        Instant currentMarketTime = currentSeqProvider.marketTimeAt(currentSeq);
        Instant now = currentSeqProvider.now();
        return MarketClockResponse.of(marketClock, currentSeq, currentMarketTime, marketClock.effectiveStatus(now));
    }

    public PageResponse<TradingStockSearchResponse> searchStocks(Pageable pageable) {
        Pageable normalized = PageableUtil.normalize(pageable);
        Page<Stocks> stockPage = stocksRepository.findAll(normalized);

        Map<String, Quote> quoteBySymbol = readAllQuotesSafely(
                stockPage.getContent().stream().map(Stocks::getSymbol).toList());

        Page<TradingStockSearchResponse> mapped = stockPage.map(stock -> {
            Quote quote = quoteBySymbol.get(stock.getSymbol());
            return TradingStockSearchResponse.from(
                    stock,
                    quote != null ? quote.price() : null,
                    quote != null ? quote.marketTime() : null);
        });

        return PageResponse.of(mapped);
    }

    public TradingStockFindResponse findStocksBySymbol(String symbol) {
        Stocks stock = getStockBySymbol(symbol);
        Quote quote = quoteReader.read(symbol);
        return TradingStockFindResponse.from(stock, quote.price(), quote.marketTime(), quote.seq());
    }

    public TradingStockDetailsFindResponse findStocksDetailsBySymbol(String symbol) {
        Stocks stock = getStockBySymbol(symbol);
        MarketClock marketClock = currentSeqProvider.getClock();
        long seq = currentSeqProvider.currentSeq(marketClock);

        PriceCandles candle = priceCandlesRepository.findBySeqAndStockId(seq, stock.getId())
                .orElseThrow(() -> new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));

        return TradingStockDetailsFindResponse.from(stock.getId(), symbol, candle);
    }

    private Stocks getStockBySymbol(String symbol) {
        return stocksRepository.findBySymbol(symbol)
                .orElseThrow(() -> new CustomException(TradingErrorCode.STOCK_NOT_FOUND));
    }

    /**
     * 목록 조회는 단건 조회와 달리 "이 페이지 전부 시세 없음"을 500으로 터뜨리지 않는다.
     * 부분 누락은 QuoteReader가 이미 조용히 걸러주고, 전멸(빈 페이지 포함)만 여기서 빈 결과로 받는다.
     */
    private Map<String, Quote> readAllQuotesSafely(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        try {
            return quoteReader.readAll(symbols).stream()
                    .collect(Collectors.toMap(Quote::symbol, Function.identity()));
        } catch (CustomException e) {
            if (e.getErrorCode() == TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ) {
                return Map.of();
            }
            throw e;
        }
    }


    // ==============================
    // = 계좌 자산
    // ==============================
}
