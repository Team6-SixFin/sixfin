package com.sparta.trading.application.service;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.position.PositionRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioQueryService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final StocksRepository stocksRepository;
    private final QuoteReader quoteReader;

    // 소수점 4자리 반올림 (계산 초기값)
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    /**
     * 추출 및 조회
     */
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(UUID userId) {

        // 사용자 계좌 조회
        Accounts account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        // 계좌의 OPEN 포지션 목록 조회(보유중)
        List<Positions> positions = positionRepository.findAllOpenByAccountId(account.getId());

        // 보유 종목 없을 시 0
        if (positions.isEmpty()) {
            return PortfolioResponse.of(
                    account,
                    ZERO,
                    ZERO,
                    ZERO,
                    null
            );
        }

        // 포지션에서 stockId 추출
        List<Long> stockIds = positions.stream().map(Positions::getStockId).distinct().toList();

        // stockId로 symbol 조회
        Map<Long, Stocks> stockById = stocksRepository.findAllById(stockIds)
                .stream().collect(Collectors.toMap(Stocks::getId, Function.identity()));

        // 존재하지 않는 종목이 있는 경우
        if (stockById.size() != stockIds.size()) {
            throw new CustomException(TradingErrorCode.STOCK_NOT_FOUND);
        }

        List<String> symbols = stockIds.stream()
                .map(stockId -> stockById.get(stockId).getSymbol()).toList();

        // 모든 종목의 현재가를 한번에 조회
        List<Quote> quotes = quoteReader.readAll(symbols);

        // 요청한 종목의 시세만 정확히 포함하는지 검증한 뒤 stockId로 색인
        Map<Long, Quote> quoteByStockId = indexQuotes(quotes, stockIds);

        BigDecimal stockValuation = ZERO; // 현재 보유주식 총 가치
        BigDecimal unrealizedProfit = ZERO; // 아직 팔지 않은 주식의 총 손익
        BigDecimal totalCost = ZERO; // 주식을 사는데 들어간 총 원금

        /**
         * 계산 로직
         */

        for (Positions position : positions) {

            Quote quote = quoteByStockId.get(position.getStockId()); // 시세 찾기
            BigDecimal quantity = BigDecimal.valueOf(position.getQuantity()); // 보유수랑
            BigDecimal averageEntryPrice = position.getAverageEntryPrice(); // 평균 매수가
            BigDecimal currentPrice = quote.price(); // 현재가

            // 평가금액 = 현재가 × 보유 수량
            // (stockValuation)
            BigDecimal positionValuation = currentPrice.multiply(quantity);
            stockValuation = stockValuation.add(positionValuation);

            // 매수 원금 = 평균 매수가 × 보유 수량
            BigDecimal positionCost = averageEntryPrice.multiply(quantity);
            totalCost = totalCost.add(positionCost);

            // 미실현 손익 = (현재가 - 평균 매수가) × 보유 수량
            // (unrealizedProfit)
            BigDecimal positionProfit = currentPrice
                    .subtract(averageEntryPrice)
                    .multiply(quantity);
            unrealizedProfit = unrealizedProfit.add(positionProfit);
        }

        // 미실현 수익률 계산
        // 수익률 = 미실현 손익 ÷ 매수 원금 × 100
        BigDecimal unrealizedReturnRate = ZERO;

        // 매수 원금이 0일 때 0으로 처리해 0으로 나누는 오류 방지
        if (totalCost.signum() != 0) {
            unrealizedReturnRate = unrealizedProfit
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalCost, 4, RoundingMode.HALF_UP);
        }

        // 현재 시세 기준 시각
        Instant marketTime = quotes.get(0).marketTime();

        // 계산 결과를 응답 DTO로 변환
        // totalAssets는 PortfolioResponse.of() 내부에서 계산
        return PortfolioResponse.of(
                account,
                money(stockValuation),
                money(unrealizedProfit),
                money(unrealizedReturnRate),
                marketTime
        );
    }

    /**
     * QuoteReader가 반환한 시세가 요청한 종목과 정확히 일치하는지 검증하고
     * 이후 빠르게 조회할 수 있도록 stockId를 키로 하는 Map으로 변환
     */
    private Map<Long, Quote> indexQuotes(
            List<Quote> quotes,
            List<Long> requestedStockIds
    ) {
        Map<Long, Quote> quoteByStockId = new HashMap<>();

        for (Quote quote : quotes) {
            // quote가 null 이거나 stockId가 null 이면 거절
            if (quote == null || quote.stockId() == null) {
                throw new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
            }

            Quote previous = quoteByStockId.putIfAbsent(quote.stockId(), quote);
            // 같은 종목에 대한 시세가 둘 이상이면 어느 값을 사용해야 할지 몰라 거절
            if (previous != null) {
                throw new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
            }
        }

        // Map의 키 집합과 요청 종목 ID 집합이 같은지 비교
        if (!quoteByStockId.keySet().equals(Set.copyOf(requestedStockIds))) {
            throw new CustomException(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ);
        }

        return quoteByStockId;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
