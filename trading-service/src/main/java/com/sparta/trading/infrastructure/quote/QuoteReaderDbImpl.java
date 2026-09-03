package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** DB만 사용하는 QuoteReader 구현. Redis 캐싱 구현체와 나란히 두고 설정으로 스위치할 예정. */
@Component
@RequiredArgsConstructor
public class QuoteReaderDbImpl implements QuoteReader {

    private final StocksRepository stocksRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final CurrentSeqProvider currentSeqProvider;

    @Override
    public Quote read(String symbol) {
        return readAll(List.of(symbol)).stream()
                .findFirst()
                .orElseThrow(() -> new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

    /**
     * @param symbolList
     * @return Quote. 종목 symbolList 중 현재 seq에 데이터가 없는 종목은 제외.
     * 현재 seq에 데이터가 있는 종목이 없으면 {@code PRICE_CANDLE_NOT_FOUND_FOR_SEQ} 반환
     */
    @Override
    public List<Quote> readAll(List<String> symbolList) {
        List<Long> stockIdList = stocksRepository.findIdBySymbolIn(symbolList);
        if (stockIdList.size() != symbolList.size()) {
            // todo 목록 중에서 없는 항목은 따로 알려주기
            throw new CustomException(TradingErrorCode.STOCK_NOT_FOUND);
        }

        MarketClock marketClock = currentSeqProvider.getClock();
        long seq = currentSeqProvider.currentSeq(marketClock);

        List<PriceCandles> candles = priceCandlesRepository.findAllBySeqAndStockIdIn(
                seq, stockIdList);
        if(candles.isEmpty()){
            throw new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
        }

        return candles.stream()
                .map(candle -> toQuote(candle, candle.getStock().getSymbol(), marketClock))
                .toList();
    }

    private Quote toQuote(PriceCandles candle, String symbol, MarketClock marketClock) {
        return new Quote(
                symbol,
                candle.getClosePrice(),
                candle.getSeq(),
                candle.getMarketTime(),
                marketClock.getStatus(),
                candle.getRecent20dHigh(),
                candle.getRecent20dLow(),
                candle.getRecent5dReturn(),
                currentSeqProvider.now(),
                candle.getStock().getId(),
                candle.getStock().getName()
        );
    }
}
