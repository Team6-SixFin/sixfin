package com.sparta.trading.application.service;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.position.PositionRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.PortfolioResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PortfolioQueryServiceTest {

    private static final Instant MARKET_TIME = Instant.parse("2026-09-04T01:30:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private StocksRepository stocksRepository;

    @Mock
    private QuoteReader quoteReader;

    @InjectMocks
    private PortfolioQueryService portfolioQueryService;

    @Test
    void getPortfolio_throwsWhenAccountDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioQueryService.getPortfolio(userId))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TradingErrorCode.ACCOUNT_NOT_FOUND));

        verifyNoInteractions(positionRepository, stocksRepository, quoteReader);
    }

    @Test
    void getPortfolio_returnsCashOnlyWhenThereAreNoOpenPositions() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Accounts account = accountOf(accountId, userId, "100000.0000");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(accountId)).thenReturn(List.of());

        PortfolioResponse response = portfolioQueryService.getPortfolio(userId);

        assertThat(response.cashBalance()).isEqualByComparingTo("100000.0000");
        assertThat(response.stockValuation()).isEqualByComparingTo("0.0000");
        assertThat(response.totalAssets()).isEqualByComparingTo("100000.0000");
        assertThat(response.unrealizedProfit()).isEqualByComparingTo("0.0000");
        assertThat(response.unrealizedReturnRate()).isEqualByComparingTo("0.0000");
        assertThat(response.marketTime()).isNull();
        verifyNoInteractions(stocksRepository, quoteReader);
    }

    @Test
    void getPortfolio_calculatesValuationProfitAndReturnRate() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Accounts account = accountOf(accountId, userId, "100000.0000");
        Positions position = positionOf(accountId, userId, 1L, 10, "200.0000");
        Stocks stock = stockOf(1L, "AAPL");
        Quote quote = quoteOf(1L, "AAPL", "220.0000");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(accountId)).thenReturn(List.of(position));
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));
        when(quoteReader.readAll(List.of("AAPL"))).thenReturn(List.of(quote));

        PortfolioResponse response = portfolioQueryService.getPortfolio(userId);

        // 평가금액 = 현재가 220 × 보유수량 10
        assertThat(response.stockValuation()).isEqualByComparingTo("2200.0000");
        // 미실현 손익 = (현재가 220 - 평균 매입가 200) × 보유수량 10
        assertThat(response.unrealizedProfit()).isEqualByComparingTo("200.0000");
        // 총자산 = 예수금 100000 + 평가금액 2200
        assertThat(response.totalAssets()).isEqualByComparingTo("102200.0000");
        // 수익률 = 미실현 손익 200 ÷ 매수 원금 2000 × 100
        assertThat(response.unrealizedReturnRate()).isEqualByComparingTo("10.0000");
        assertThat(response.marketTime()).isEqualTo(MARKET_TIME);

        verify(quoteReader).readAll(List.of("AAPL"));
    }

    @Test
    void getPortfolio_throwsWhenStockDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Accounts account = accountOf(accountId, userId, "100000.0000");
        Positions position = positionOf(accountId, userId, 1L, 10, "200.0000");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(accountId)).thenReturn(List.of(position));
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> portfolioQueryService.getPortfolio(userId))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TradingErrorCode.STOCK_NOT_FOUND));

        verifyNoInteractions(quoteReader);
    }

    @Test
    void getPortfolio_throwsWhenQuoteIsMissingForAnyPosition() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Accounts account = accountOf(accountId, userId, "100000.0000");
        Positions position = positionOf(accountId, userId, 1L, 10, "200.0000");
        Stocks stock = stockOf(1L, "AAPL");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(accountId)).thenReturn(List.of(position));
        when(stocksRepository.findAllById(List.of(1L))).thenReturn(List.of(stock));
        when(quoteReader.readAll(List.of("AAPL"))).thenReturn(List.of());

        assertThatThrownBy(() -> portfolioQueryService.getPortfolio(userId))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TradingErrorCode.PRICE_CANDLE_NOT_FOUND_FOR_SEQ));
    }

    private Accounts accountOf(UUID accountId, UUID userId, String cashBalance) {
        Accounts account = org.mockito.Mockito.mock(Accounts.class);
        when(account.getId()).thenReturn(accountId);
        lenient().when(account.getCashBalance()).thenReturn(new BigDecimal(cashBalance));
        return account;
    }

    private Positions positionOf(UUID accountId, UUID userId, Long stockId,
                                 int quantity, String averageEntryPrice) {
        return Positions.open(
                accountId,
                stockId,
                quantity,
                new BigDecimal(averageEntryPrice),
                null,
                null,
                MARKET_TIME,
                1L,
                userId
        );
    }

    private Stocks stockOf(Long stockId, String symbol) {
        Stocks stock = org.mockito.Mockito.mock(Stocks.class);
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
