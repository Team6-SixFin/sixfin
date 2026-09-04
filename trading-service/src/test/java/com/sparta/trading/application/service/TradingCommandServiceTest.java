package com.sparta.trading.application.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.OrderRejectReason;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderStatus;
import com.sparta.trading.domain.entity.OrderType;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.Stocks;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import com.sparta.trading.domain.repository.execution.ExecutionRepository;
import com.sparta.trading.domain.repository.order.OrderRepository;
import com.sparta.trading.domain.repository.outbox.OutboxEventRepository;
import com.sparta.trading.domain.repository.position.PositionRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TradingCommandServiceTest {

    private static final Instant QUOTE_TIME = Instant.parse("2026-09-03T13:30:00Z");
    private static final Instant EXECUTED_AT = Instant.parse("2026-09-03T13:31:00Z");

    @Mock private QuoteReader quoteReader;
    @Mock private StocksRepository stocksRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private ExecutionRepository executionRepository;
    @Mock private CashLedgerRepository cashLedgerRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private TradingCommandService service;

    @BeforeEach
    void setUp() {
        service = new TradingCommandService(
                quoteReader,
                stocksRepository,
                accountRepository,
                orderRepository,
                positionRepository,
                executionRepository,
                cashLedgerRepository,
                outboxEventRepository,
                JsonMapper.builder().addModule(new JavaTimeModule()).build()
        );
        lenient().when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(positionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cashLedgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(outboxEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void placeOrder_createsPositionExecutionCashLedgerAndLearningCompatibleOutboxInOneFlow() {
        UUID userId = UUID.randomUUID();
        Accounts account = accountOf(userId);
        PlaceOrderCommand command = command(UUID.randomUUID(), " aapl ", 10);
        when(orderRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(quoteReader.read("AAPL")).thenReturn(validQuote("100.0000"));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findOpenByAccountIdAndStockIdForUpdate(account.getId(), 1L))
                .thenReturn(Optional.empty());

        OrderResponse response = service.placeOrder(userId, command);

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.cashBalance()).isEqualByComparingTo("99000.0000");
        assertThat(response.positionId()).isNotNull();
        assertThat(response.execution()).isNotNull();
        assertThat(account.getCashBalance()).isEqualByComparingTo("99000.0000");
        verify(accountRepository).findByUserIdForUpdate(userId);
        verify(positionRepository).save(any());
        verify(executionRepository).save(any());
        verify(cashLedgerRepository).save(any(CashLedgers.class));

        ArgumentCaptor<OutboxEvents> outboxCaptor = ArgumentCaptor.forClass(OutboxEvents.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvents outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("EXECUTION");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(response.execution().executionId());
        assertThat(outboxEvent.getPartitionKey()).isEqualTo(userId.toString());
        assertThat(outboxEvent.getPayload().path("eventType").asText()).isEqualTo("BUY_EXECUTED");
        assertThat(outboxEvent.getPayload().path("payload").path("isNewPosition").asBoolean()).isTrue();
        assertThat(outboxEvent.getPayload().path("payload").path("stockId").asLong()).isEqualTo(1L);
        assertThat(outboxEvent.getPayload().path("payload").path("marketContext")
                .path("recent20DayHigh").decimalValue()).isEqualByComparingTo("110.0000");
    }

    @Test
    void placeOrder_persistsRejectedResultWithoutChangingCashWhenBalanceIsInsufficient() {
        UUID userId = UUID.randomUUID();
        Accounts account = accountOf(userId);
        PlaceOrderCommand command = command(UUID.randomUUID(), "AAPL", 1001);
        when(orderRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(quoteReader.read("AAPL")).thenReturn(validQuote("100.0000"));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        OrderResponse response = service.placeOrder(userId, command);

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo(OrderRejectReason.INSUFFICIENT_CASH.name());
        assertThat(response.execution()).isNull();
        assertThat(response.cashBalance()).isEqualByComparingTo("100000.0000");
        verify(orderRepository).save(any(Orders.class));
        verify(positionRepository, never()).save(any());
        verify(executionRepository, never()).save(any());
        verify(cashLedgerRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void placeOrder_rejectsWithoutOutboxWhenLearningRequiredMarketContextIsMissing() {
        UUID userId = UUID.randomUUID();
        Accounts account = accountOf(userId);
        PlaceOrderCommand command = command(UUID.randomUUID(), "AAPL", 1);
        when(orderRepository.findByRequestId(command.requestId())).thenReturn(Optional.empty());
        when(quoteReader.read("AAPL")).thenReturn(new Quote(
                "AAPL", new BigDecimal("100.0000"), 12L, QUOTE_TIME, ClockStatus.RUNNING,
                null, new BigDecimal("90.0000"), new BigDecimal("3.2500"), EXECUTED_AT, 1L, "Apple Inc."
        ));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));

        OrderResponse response = service.placeOrder(userId, command);

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo(OrderRejectReason.MARKET_CONTEXT_UNAVAILABLE.name());
        assertThat(account.getCashBalance()).isEqualByComparingTo("100000.0000");
        verify(positionRepository, never()).save(any());
        verify(executionRepository, never()).save(any());
        verify(cashLedgerRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void placeOrder_returnsSavedResultForSameRequestIdWithoutReadingQuoteAgain() {
        UUID userId = UUID.randomUUID();
        Accounts account = accountOf(userId);
        UUID requestId = UUID.randomUUID();
        PlaceOrderCommand command = command(requestId, "AAPL", 1);
        when(orderRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(quoteReader.read("AAPL")).thenReturn(validQuote("100.0000"));
        when(accountRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(account));
        when(positionRepository.findOpenByAccountIdAndStockIdForUpdate(account.getId(), 1L))
                .thenReturn(Optional.empty());

        OrderResponse first = service.placeOrder(userId, command);

        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository).save(orderCaptor.capture());
        Orders saved = orderCaptor.getValue();
        when(orderRepository.findByRequestId(requestId)).thenReturn(Optional.of(saved));
        when(stocksRepository.findBySymbol("AAPL")).thenReturn(Optional.of(stock(1L)));
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(executionRepository.findByOrderId(saved.getId())).thenReturn(Optional.of(
                com.sparta.trading.domain.entity.Executions.buy(
                        saved.getId(), saved.getPositionId(), userId, 1L, 1, new BigDecimal("100.0000"),
                        new BigDecimal("100.0000"), 12L, QUOTE_TIME
                )
        ));

        OrderResponse retry = service.placeOrder(userId, command);

        assertThat(retry.orderId()).isEqualTo(first.orderId());
        verify(quoteReader, times(1)).read("AAPL");
        verify(accountRepository, times(1)).findByUserIdForUpdate(eq(userId));
    }

    @Test
    void placeOrder_rejectsDifferentRequestContentUsingExistingRequestId() {
        UUID userId = UUID.randomUUID();
        Accounts account = accountOf(userId);
        UUID requestId = UUID.randomUUID();
        Orders existing = Orders.rejected(
                requestId, account.getId(), 1L, 1, null, null,
                QUOTE_TIME, 12L, OrderRejectReason.INSUFFICIENT_CASH, userId
        );
        when(orderRepository.findByRequestId(requestId)).thenReturn(Optional.of(existing));
        when(stocksRepository.findBySymbol("AAPL")).thenReturn(Optional.of(stock(1L)));
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.placeOrder(userId, command(requestId, "AAPL", 1)))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT));
        verify(quoteReader, never()).read(any());
    }

    private PlaceOrderCommand command(UUID requestId, String symbol, int quantity) {
        return new PlaceOrderCommand(requestId, symbol, OrderSide.BUY, OrderType.MARKET,
                quantity, new BigDecimal("90.0000"), "장기 성장 기대");
    }

    private Quote validQuote(String price) {
        return new Quote(
                "AAPL", new BigDecimal(price), 12L, QUOTE_TIME, ClockStatus.RUNNING,
                new BigDecimal("110.0000"), new BigDecimal("90.0000"), new BigDecimal("3.2500"),
                EXECUTED_AT, 1L, "Apple Inc."
        );
    }

    private Accounts accountOf(UUID userId) {
        Accounts account = Accounts.create(userId);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    private Stocks stock(Long id) {
        Stocks stock = Stocks.builder()
                .symbol("AAPL")
                .name("Apple Inc.")
                .market("NASDAQ")
                .currency("USD")
                .active(true)
                .build();
        ReflectionTestUtils.setField(stock, "id", id);
        return stock;
    }
}
