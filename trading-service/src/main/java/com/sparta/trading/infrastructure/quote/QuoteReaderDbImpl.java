package com.sparta.trading.infrastructure.quote;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.application.service.CurrentSeqProvider;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.domain.entity.PriceCandles;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static com.sparta.trading.application.port.Quote.from;

/**
 * DB만 사용하는 QuoteReader 구현. 항상 빈으로 존재한다 — {@code quote.reader.type=redis}일 때는
 * {@link QuoteReaderRedisImpl}이 {@code @Primary}로 우선권을 가져가지만, 이 빈은 그 안에서
 * 캐시 미스/장애 시 폴백 대상으로 구체 타입으로 직접 주입돼 계속 쓰인다.
 */
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

        MarketClockSnapshot marketClock = currentSeqProvider.getClockSnapshot();
        long seq = currentSeqProvider.currentSeq(marketClock);

        List<PriceCandles> candles = priceCandlesRepository.findAllBySeqAndStockIdIn(
                seq, stockIdList);
        if(candles.isEmpty()){
            throw new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
        }

        Instant now = currentSeqProvider.now();
        return candles.stream()
                .map(candle -> from(candle, candle.getStock().getSymbol(), marketClock, now))
                .toList();
    }


}
