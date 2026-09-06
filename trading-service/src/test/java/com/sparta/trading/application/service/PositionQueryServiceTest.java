package com.sparta.trading.application.service;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.position.PositionRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.PositionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionQueryServiceTest {

    private static final Instant MARKET_TIME = Instant.parse("2026-09-04T01:30:00Z");

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private StocksRepository stocksRepository;

    @Mock
    private QuoteReader quoteReader;

    @InjectMocks
    private PositionQueryService positionQueryService;

    @Test
    void getPositions_returnsOpenPositionWithCurrentPriceAndUnrealizedProfit() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Positions position = positionOf(
                positionId,
                1L,
                PositionStatus.OPEN,
                10,
                "200.0000",
                MARKET_TIME,
                null
        );
        Stocks stock = stockOf(1L, "AAPL", "Apple Inc.");
        Quote quote = quoteOf(1L, "AAPL", "220.0000");
        Page<Positions> positionPage = new PageImpl<>(List.of(position), pageable, 1);

        when(positionRepository.findAllByUserIdAndStatus(userId, PositionStatus.OPEN, pageable))
                .thenReturn(positionPage);
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));
        when(quoteReader.readAll(List.of("AAPL"))).thenReturn(List.of(quote));

        PageResponse<PositionResponse> response = positionQueryService.getPositions(
                userId,
                PositionStatus.OPEN,
                pageable
        );

        assertThat(response.getContent()).singleElement().satisfies(positionResponse -> {
            assertThat(positionResponse.positionId()).isEqualTo(positionId);
            assertThat(positionResponse.symbol()).isEqualTo("AAPL");
            assertThat(positionResponse.name()).isEqualTo("Apple Inc.");
            assertThat(positionResponse.status()).isEqualTo(PositionStatus.OPEN);
            assertThat(positionResponse.currentPrice()).isEqualByComparingTo("220.0000");
            assertThat(positionResponse.unrealizedProfit()).isEqualByComparingTo("200.0000");
        });
        assertThat(response.getPageInfo().getPage()).isZero();
        assertThat(response.getPageInfo().getSize()).isEqualTo(20);
        assertThat(response.getPageInfo().getTotalElements()).isEqualTo(1);
        assertThat(response.getSummary()).isNull();

        verify(quoteReader).readAll(List.of("AAPL"));
    }

    @Test
    void getPositions_returnsClosedPositionWithoutQuote() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Positions position = positionOf(
                positionId,
                1L,
                PositionStatus.CLOSED,
                10,
                "200.0000",
                MARKET_TIME,
                MARKET_TIME.plusSeconds(3600)
        );
        Stocks stock = stockOf(1L, "AAPL", "Apple Inc.");
        Page<Positions> positionPage = new PageImpl<>(List.of(position), pageable, 1);

        when(positionRepository.findAllByUserIdAndStatus(userId, PositionStatus.CLOSED, pageable))
                .thenReturn(positionPage);
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));

        PageResponse<PositionResponse> response = positionQueryService.getPositions(
                userId,
                PositionStatus.CLOSED,
                pageable
        );

        assertThat(response.getContent()).singleElement().satisfies(positionResponse -> {
            assertThat(positionResponse.status()).isEqualTo(PositionStatus.CLOSED);
            assertThat(positionResponse.currentPrice()).isNull();
            assertThat(positionResponse.unrealizedProfit()).isNull();
            assertThat(positionResponse.closedAt()).isEqualTo(MARKET_TIME.plusSeconds(3600));
        });
        verifyNoInteractions(quoteReader);
    }

    @Test
    void getPositions_throwsWhenStockDoesNotExist() {
        UUID userId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Positions position = positionWithStockId(1L);
        Page<Positions> positionPage = new PageImpl<>(List.of(position), pageable, 1);

        when(positionRepository.findAllByUserIdAndStatus(userId, PositionStatus.OPEN, pageable))
                .thenReturn(positionPage);
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> positionQueryService.getPositions(userId, PositionStatus.OPEN, pageable))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TradingErrorCode.STOCK_NOT_FOUND));

        verifyNoInteractions(quoteReader);
    }

    @Test
    void getPositions_throwsWhenQuoteIsMissingForOpenPosition() {
        UUID userId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Positions position = positionWithStockId(1L);
        Stocks stock = stockWithSymbol(1L, "AAPL");
        Page<Positions> positionPage = new PageImpl<>(List.of(position), pageable, 1);

        when(positionRepository.findAllByUserIdAndStatus(userId, PositionStatus.OPEN, pageable))
                .thenReturn(positionPage);
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));
        when(quoteReader.readAll(List.of("AAPL"))).thenReturn(List.of());

        assertThatThrownBy(() -> positionQueryService.getPositions(userId, PositionStatus.OPEN, pageable))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

    private Positions positionOf(
            UUID positionId,
            Long stockId,
            PositionStatus status,
            int quantity,
            String averageEntryPrice,
            Instant openedAt,
            Instant closedAt
    ) {
        Positions position = mock(Positions.class);
        when(position.getId()).thenReturn(positionId);
        when(position.getStockId()).thenReturn(stockId);
        when(position.getStatus()).thenReturn(status.name());
        when(position.getQuantity()).thenReturn(quantity);
        when(position.getAverageEntryPrice()).thenReturn(new BigDecimal(averageEntryPrice));
        when(position.getOpenedAt()).thenReturn(openedAt);
        when(position.getClosedAt()).thenReturn(closedAt);
        return position;
    }

    private Positions positionWithStockId(Long stockId) {
        Positions position = mock(Positions.class);
        when(position.getStockId()).thenReturn(stockId);
        return position;
    }

    private Stocks stockOf(Long stockId, String symbol, String name) {
        Stocks stock = mock(Stocks.class);
        when(stock.getId()).thenReturn(stockId);
        when(stock.getSymbol()).thenReturn(symbol);
        when(stock.getName()).thenReturn(name);
        return stock;
    }

    private Stocks stockWithSymbol(Long stockId, String symbol) {
        Stocks stock = mock(Stocks.class);
        when(stock.getId()).thenReturn(stockId);
        when(stock.getSymbol()).thenReturn(symbol);
        return stock;
    }

    private Quote quoteOf(Long stockId, String symbol, String price) {
        return new Quote(
                symbol,
                new BigDecimal(price),
                100L,
                MARKET_TIME,
                ClockStatus.RUNNING,
                null,
                null,
                null,
                MARKET_TIME,
                stockId,
                symbol + " Inc."
        );
    }
}
