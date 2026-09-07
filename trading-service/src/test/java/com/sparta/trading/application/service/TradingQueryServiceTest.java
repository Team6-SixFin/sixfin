package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingAdminSearchOrderQuery;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.entity.OrderRejectReason;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.execution.ExecutionRepository;
import com.sparta.trading.domain.repository.order.OrderRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.GlobalErrorCode;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.infrastructure.persistence.repository.candles.PriceCandlesRepository;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.TradingExecutionResponseDto;
import com.sparta.trading.presentation.dto.response.TradingOrderDetailResponseDto;
import com.sparta.trading.presentation.dto.response.TradingOrderResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingQueryServiceTest {

    private static final Instant MARKET_TIME = Instant.parse("2026-09-03T13:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private CurrentSeqProvider currentSeqProvider;
    @Mock private StocksRepository stocksRepository;
    @Mock private PriceCandlesRepository priceCandlesRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TradingOrderQueryRepository tradingOrderQueryRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private QuoteReader quoteReader;

    private TradingQueryService service;

    @BeforeEach
    void setUp() {
        service = new TradingQueryService(
                currentSeqProvider,
                stocksRepository,
                priceCandlesRepository,
                orderRepository,
                tradingOrderQueryRepository,
                executionRepository,
                accountRepository,
                quoteReader
        );
    }

    // ==============================
    // = searchOrder
    // ==============================

    @Test
    void searchOrder_returnsEmptyPageWhenAccountMissing() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        PageResponse<TradingOrderResponseDto> result = service.searchOrder(
                USER_ID, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(tradingOrderQueryRepository, never()).searchOrder(any(), any(), anyList(), any());
    }

    @Test
    void searchOrder_throwsInvalidRequestWhenStatusIsInvalid() {
        assertThatThrownBy(() -> service.searchOrder(USER_ID, "NOPE", null, null, PageRequest.of(0, 20)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(GlobalErrorCode.INVALID_REQUEST));
        verify(accountRepository, never()).findByUserId(any());
    }

    @Test
    void searchOrder_throwsInvalidRequestWhenSideIsInvalid() {
        assertThatThrownBy(() -> service.searchOrder(USER_ID, null, "NOPE", null, PageRequest.of(0, 20)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(GlobalErrorCode.INVALID_REQUEST));
    }

    @Test
    void searchOrder_mapsEachOrderToItsOwnStockAcrossMultipleSymbols() {
        Accounts account = accountOf(USER_ID);
        Stocks aapl = stock(1L, "AAPL", "Apple Inc.");
        Stocks msft = stock(2L, "MSFT", "Microsoft Corporation");
        Orders aaplOrder = filledOrder(account.getId(), aapl.getId());
        Orders msftOrder = filledOrder(account.getId(), msft.getId());
        Pageable pageable = PageRequest.of(0, 20);

        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(tradingOrderQueryRepository.searchOrder(any(), eq(null), eq(List.of(account.getId())), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(aaplOrder, msftOrder), pageable, 2));
        when(stocksRepository.findAllById(anyList())).thenReturn(List.of(aapl, msft));

        PageResponse<TradingOrderResponseDto> result = service.searchOrder(USER_ID, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TradingOrderResponseDto::symbol)
                .containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    void searchOrder_passesSentinelStockIdWhenSymbolDoesNotExist() {
        Accounts account = accountOf(USER_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(stocksRepository.findBySymbol("NOPE")).thenReturn(Optional.empty());
        when(tradingOrderQueryRepository.searchOrder(any(), eq(-1L), eq(List.of(account.getId())), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<TradingOrderResponseDto> result = service.searchOrder(USER_ID, null, null, "NOPE", pageable);

        assertThat(result.getContent()).isEmpty();
        verify(tradingOrderQueryRepository).searchOrder(any(), eq(-1L), eq(List.of(account.getId())), eq(pageable));
    }

    @Test
    void searchOrder_passesStatusAndSideThroughToQuery() {
        Accounts account = accountOf(USER_ID);
        Pageable pageable = PageRequest.of(0, 20);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(tradingOrderQueryRepository.searchOrder(any(), any(), anyList(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.searchOrder(USER_ID, "FILLED", "BUY", null, pageable);

        ArgumentCaptor<TradingAdminSearchOrderQuery> captor = ArgumentCaptor.forClass(TradingAdminSearchOrderQuery.class);
        verify(tradingOrderQueryRepository).searchOrder(captor.capture(), any(), anyList(), any());
        assertThat(captor.getValue().status()).isEqualTo("FILLED");
        assertThat(captor.getValue().side()).isEqualTo("BUY");
    }

    // ==============================
    // = findOrderById
    // ==============================

    @Test
    void findOrderById_returnsDetailWithExecutionWhenFilled() {
        Accounts account = accountOf(USER_ID);
        Stocks stock = stock(1L, "AAPL", "Apple Inc.");
        Orders order = filledOrder(account.getId(), stock.getId());
        Executions execution = Executions.buy(
                order.getId(), order.getPositionId(), USER_ID, stock.getId(),
                10, new BigDecimal("100.0000"), new BigDecimal("100.0000"), 12L, MARKET_TIME);

        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(stocksRepository.findById(stock.getId())).thenReturn(Optional.of(stock));
        when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.of(execution));

        TradingOrderDetailResponseDto result = service.findOrderById(USER_ID, order.getId());

        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.execution()).isNotNull();
        assertThat(result.execution().executedQuantity()).isEqualTo(10);
    }

    @Test
    void findOrderById_returnsNullExecutionWhenRejected() {
        Accounts account = accountOf(USER_ID);
        Stocks stock = stock(1L, "AAPL", "Apple Inc.");
        Orders order = Orders.rejected(
                UUID.randomUUID(), account.getId(), stock.getId(), 10, null, null,
                MARKET_TIME, 12L, OrderRejectReason.INSUFFICIENT_CASH, USER_ID);

        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(stocksRepository.findById(stock.getId())).thenReturn(Optional.of(stock));
        when(executionRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());

        TradingOrderDetailResponseDto result = service.findOrderById(USER_ID, order.getId());

        assertThat(result.execution()).isNull();
        assertThat(result.rejectReason()).isEqualTo(OrderRejectReason.INSUFFICIENT_CASH.name());
    }

    @Test
    void findOrderById_throwsAccountNotFoundWhenNoAccount() {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrderById(USER_ID, UUID.randomUUID()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Test
    void findOrderById_throwsOrderNotFoundWhenOrderMissing() {
        Accounts account = accountOf(USER_ID);
        UUID orderId = UUID.randomUUID();
        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrderById(USER_ID, orderId))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void findOrderById_throwsOrderNotFoundWhenOrderBelongsToAnotherAccount() {
        Accounts myAccount = accountOf(USER_ID);
        Accounts otherAccount = accountOf(UUID.randomUUID());
        Orders someoneElsesOrder = filledOrder(otherAccount.getId(), 1L);

        when(accountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(myAccount));
        when(orderRepository.findById(someoneElsesOrder.getId())).thenReturn(Optional.of(someoneElsesOrder));

        assertThatThrownBy(() -> service.findOrderById(USER_ID, someoneElsesOrder.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
    }

    // ==============================
    // = searchExecutions
    // ==============================

    @Test
    void searchExecutions_mapsEachExecutionToItsOwnStock() {
        Stocks aapl = stock(1L, "AAPL", "Apple Inc.");
        Stocks msft = stock(2L, "MSFT", "Microsoft Corporation");
        Executions aaplExecution = Executions.buy(
                UUID.randomUUID(), UUID.randomUUID(), USER_ID, aapl.getId(),
                5, new BigDecimal("100.0000"), new BigDecimal("100.0000"), 12L, MARKET_TIME);
        Executions msftExecution = Executions.buy(
                UUID.randomUUID(), UUID.randomUUID(), USER_ID, msft.getId(),
                3, new BigDecimal("200.0000"), new BigDecimal("200.0000"), 12L, MARKET_TIME);
        Pageable pageable = PageRequest.of(0, 20);

        when(executionRepository.search(eq(USER_ID), eq(null), eq(null), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(aaplExecution, msftExecution), pageable, 2));
        when(stocksRepository.findAllById(anyList())).thenReturn(List.of(aapl, msft));

        PageResponse<TradingExecutionResponseDto> result =
                service.searchExecutions(USER_ID, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TradingExecutionResponseDto::symbol)
                .containsExactlyInAnyOrder("AAPL", "MSFT");
    }

    @Test
    void searchExecutions_throwsInvalidRequestWhenSideIsInvalid() {
        assertThatThrownBy(() -> service.searchExecutions(USER_ID, null, null, "NOPE", PageRequest.of(0, 20)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(GlobalErrorCode.INVALID_REQUEST));
    }

    @Test
    void searchExecutions_passesSentinelStockIdWhenSymbolDoesNotExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(stocksRepository.findBySymbol("NOPE")).thenReturn(Optional.empty());
        when(executionRepository.search(eq(USER_ID), eq(null), eq(-1L), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<TradingExecutionResponseDto> result =
                service.searchExecutions(USER_ID, null, "NOPE", null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(executionRepository).search(eq(USER_ID), eq(null), eq(-1L), eq(null), eq(pageable));
    }

    // ==============================
    // = fixtures
    // ==============================

    private Accounts accountOf(UUID userId) {
        Accounts account = Accounts.create(userId);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    private Stocks stock(Long id, String symbol, String name) {
        Stocks stock = Stocks.builder()
                .symbol(symbol)
                .name(name)
                .market("NASDAQ")
                .currency("USD")
                .active(true)
                .build();
        ReflectionTestUtils.setField(stock, "id", id);
        return stock;
    }

    private Orders filledOrder(UUID accountId, Long stockId) {
        return Orders.filled(
                UUID.randomUUID(), accountId, stockId, UUID.randomUUID(),
                10, new BigDecimal("90.0000"), "장기 성장 기대", MARKET_TIME, 12L, USER_ID);
    }
}
