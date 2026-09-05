package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.*;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.execution.ExecutionRepository;
import com.sparta.trading.domain.repository.order.OrderRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.GlobalErrorCode;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingQueryService {

    private final CurrentSeqProvider currentSeqProvider;
    private final StocksRepository stocksRepository;
    private final PriceCandlesRepository priceCandlesRepository;
    private final OrderRepository orderRepository;
    private final TradingOrderQueryRepository tradingOrderQueryRepository;
    private final ExecutionRepository executionRepository;
    private final AccountRepository accountRepository;
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


    // ==============================
    // = 매매
    // ==============================

    public PageResponse<TradingOrderResponseDto> searchOrder(UUID userId, String status, String side, String symbol, Pageable pageable) {
        Pageable normalized = PageableUtil.normalize(pageable);

        validateEnumIfPresent(status, OrderStatus.class);
        validateEnumIfPresent(side, OrderSide.class);

        // search의 경우에는 계좌가 없으면 그냥 빈 응답 반환
        Optional<Accounts> account = accountRepository.findByUserId(userId);
        if (account.isEmpty()) {
            return PageResponse.of(Page.empty(normalized));
        }

        // symbol이 있는지 확인
        Long targetStockId = resolveStockIdOrSentinel(symbol);

        // 관리자용 Search Order 로직 사용. 새로 user용 search함수를 만들어봐야 하이버네이트에서 같은 쿼리 생성
        TradingAdminSearchOrderQuery query = new TradingAdminSearchOrderQuery(userId, symbol, side, status,
                null, null, null, null, null);
        Page<Orders> orderPage = tradingOrderQueryRepository.searchOrder(
                query, targetStockId, List.of(account.get().getId()), normalized);

        Map<Long, Stocks> stockById = findStocksByIds(orderPage.getContent().stream().map(Orders::getStockId));
        return PageResponse.of(orderPage.map(order ->
                TradingOrderResponseDto.from(stockById.get(order.getStockId()), order)));
    }

    public TradingOrderDetailResponseDto findOrderById(UUID userId, UUID orderId) {
        // 유저는 자기 계좌만 확인 가능함. 계좌가 없으면 애초에 조회 불가능
        Accounts account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        Orders order = orderRepository.findById(orderId)
                .filter(o -> o.belongsTo(account.getId()))
                .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_NOT_FOUND));

        Stocks stock = stocksRepository.findById(order.getStockId())
                .orElseThrow(() -> new CustomException(TradingErrorCode.STOCK_NOT_FOUND));
        Executions execution = executionRepository.findByOrderId(order.getId()).orElse(null);

        return TradingOrderDetailResponseDto.from(stock, order, execution);
    }

    /** positionId/symbol/side는 전부 선택 필터. Executions는 userId를 직접 들고 있어 계좌 조회가 필요 없다. */
    public PageResponse<TradingExecutionResponseDto> searchExecutions(
            UUID userId, UUID positionId, String symbol, String side, Pageable pageable) {
        // 검증
        Pageable normalized = PageableUtil.normalize(pageable);
        validateEnumIfPresent(side, OrderSide.class);

        Long targetStockId = resolveStockIdOrSentinel(symbol);

        Page<Executions> executionPage = executionRepository.search(userId, positionId, targetStockId, side, normalized);

        Map<Long, Stocks> stockById = findStocksByIds(executionPage.getContent().stream().map(Executions::getStockId));
        return PageResponse.of(
                executionPage.map(e -> TradingExecutionResponseDto.from(stockById.get(e.getStockId()), e)));
    }

    /** symbol 필터가 존재하지 않는 종목이면 -1을 줘서 결과가 확실히 비도록 한다. */
    private Long resolveStockIdOrSentinel(String symbol) {
        if (symbol == null) {
            return null;
        }
        return stocksRepository.findBySymbol(symbol).map(Stocks::getId).orElse(-1L);
    }

    private Map<Long, Stocks> findStocksByIds(Stream<Long> stockIds) {
        List<Stocks> allById = stocksRepository.findAllById(stockIds.distinct().toList());
        return allById.stream()
                .collect(Collectors.toMap(Stocks::getId, Function.identity()));
    }

    private <E extends Enum<E>> void validateEnumIfPresent(String value, Class<E> enumType) {
        if (value == null) {
            return;
        }
        try {
            Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new CustomException(GlobalErrorCode.INVALID_REQUEST);
        }
    }

}
