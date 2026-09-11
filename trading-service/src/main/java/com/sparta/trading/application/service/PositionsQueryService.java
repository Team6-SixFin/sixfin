package com.sparta.trading.application.service;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.positions.PositionsQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.global.util.PageableUtil;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.PositionDetailResponse;
import com.sparta.trading.presentation.dto.response.PositionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionsQueryService {

    private final PositionsQueryRepository positionRepository;
    private final StocksRepository stocksRepository;
    private final QuoteReader quoteReader;

    @Transactional(readOnly = true)
    public PageResponse<PositionResponse> getPositions(
            UUID userId,
            PositionStatus status,
            Pageable pageable
    ) {
        Pageable normalizedPageable = PageableUtil.normalize(pageable);

        // 포지션 목록 조회 (사용자 + 상태)
        Page<Positions> positionPage = positionRepository.findAllByUserIdAndStatus(
                userId,
                status,
                normalizedPageable
        );

        // stockId들을 List화
        List<Long> stockIds = positionPage.getContent().stream().
                map(Positions::getStockId).distinct().toList();

        // stockId로 종목 정보를 조회할 수 있도록 Map으로 변환
        Map<Long, Stocks> stockById = stocksRepository.findAllById(stockIds).stream().
                collect(Collectors.toMap(Stocks::getId, Function.identity()));

        // 찾을 수 없거나 존재하지 않는 종목이 있는 경우
        if (stockById.size() != stockIds.size()) {
            throw new CustomException(TradingErrorCode.STOCK_NOT_FOUND);
        }

        // 포지션의 종목별 현재 시세를 조회해서 stockId 기준으로 정리
        Map<Long, Quote> quoteByStockId = getQuoteByStockId(
                status,
                stockIds,
                stockById
        );

        // 포지션에 종목 정보와 현재 시세를 합쳐서 응답 데이터로 생성
        Page<PositionResponse> responsePage = positionPage.map(position ->
                toResponse(
                        position,
                        stockById.get(position.getStockId()),
                        quoteByStockId.get(position.getStockId())
                )
        );
        return PageResponse.of(responsePage);
    }

    @Transactional(readOnly = true)
    public PositionDetailResponse getPositionDetail(UUID userId, UUID positionId) {

        Positions position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.POSITION_NOT_FOUND));

        Stocks stock = stocksRepository.findById(position.getStockId())
                .orElseThrow(() -> new CustomException(TradingErrorCode.STOCK_NOT_FOUND));

        PositionStatus status = PositionStatus.valueOf(position.getStatus());

        Quote quote = null;
        if (status == PositionStatus.OPEN) {
            quote = quoteReader.read(stock.getSymbol());
        }

        return toDetailResponse(position, stock, status, quote);
    }

    /**
     * OPEN 포지션의 종목별 현재 시세를 일괄 조회함
     * CLOSED 포지션이나 조회할 종목이 없으면 빈 Map을 반환함
     * 요청한 종목의 시세가 일부라도 없으면 예외를 발생시킴
     */
    private Map<Long, Quote> getQuoteByStockId(
            PositionStatus status,
            List<Long> stockIds,
            Map<Long, Stocks> stockById
    ) {
        // CLOSED이거나 없으면 빈 Map으로 반환
        if (status == PositionStatus.CLOSED || stockIds.isEmpty()) {
            return Map.of();
        }

        // QuoteReader는 symbol 목록으로 시세를 조회하므로 stockId로 찾은 Stocks 객체에서 symbol만 추출
        List<String> symbols = stockIds.stream()
                .map(stockId -> stockById.get(stockId).getSymbol()).toList();

        // 요청한 종목의 시세만 정확히 포함하는지 검증한 뒤 stockId로 색인해 반환
        return indexQuotes(quoteReader.readAll(symbols), stockIds);
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

    /**
     * 포지션 엔티티, 종목 정보, 현재 시세를 목록 응답 DTO로 변환
     */
    private PositionResponse toResponse(
            Positions position,
            Stocks stock,
            Quote quote
    ) {
        // 엔티티의 String 타입 status를 응답용 PositionStatus enum으로 변환
        PositionStatus positionStatus = PositionStatus.valueOf(position.getStatus());

        // CLOSED 포지션은 현재가와 미실현 손익을 응답하지 않아서 null
        BigDecimal currentPrice = null;
        BigDecimal unrealizedProfit = null;

        // OPEN 포지션일 때 현재가랑 미실현 손익을 계산함
        if (positionStatus == PositionStatus.OPEN) {
            currentPrice = quote.price();

            // 미실현 손익 = (현재가 - 평균 매입 단가) × 현재 보유 수량
            unrealizedProfit = money(
                    currentPrice
                            .subtract(position.getAverageEntryPrice())
                            .multiply(BigDecimal.valueOf(position.getQuantity()))
            );
        }

        // 응답 데이터 조립 (엔티티, 종목, 시세)
        return new PositionResponse(
                position.getId(),
                stock.getSymbol(),
                stock.getName(),
                positionStatus,
                position.getQuantity(),
                position.getAverageEntryPrice(),
                currentPrice,
                unrealizedProfit,
                position.getOpenedAt(),
                position.getClosedAt()
        );
    }

    private PositionDetailResponse toDetailResponse(
            Positions position,
            Stocks stock,
            PositionStatus status,
            Quote quote
    ) {
        BigDecimal currentPrice = null;
        BigDecimal unrealizedProfit = null;

        if (status == PositionStatus.OPEN) {
            currentPrice = quote.price();
            unrealizedProfit = calculateUnrealizedProfit(position, currentPrice);
        }

        return new PositionDetailResponse(
                position.getId(),
                position.getStockId(),
                stock.getSymbol(),
                stock.getName(),
                status,
                position.getQuantity(),
                position.getAverageEntryPrice(),
                currentPrice,
                unrealizedProfit,
                position.getPlannedStopLossPrice(),
                position.getInvestmentReason(),
                position.getTotalBuyQuantity(),
                position.getTotalSellQuantity(),
                position.getRealizedProfit(),
                position.getOpenedAt(),
                position.getClosedAt()
        );
    }

    // 미실현 손익 계산 = (현재가 - 평균 매입 단가) × 현재 보유 수량
    private BigDecimal calculateUnrealizedProfit(
            Positions position,
            BigDecimal currentPrice
    ) {
        return money(
                currentPrice
                        .subtract(position.getAverageEntryPrice())
                        .multiply(BigDecimal.valueOf(position.getQuantity()))
        );
    }

    // 소수점 넷째 자리까지 반올림 메서드
    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
