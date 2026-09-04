package com.sparta.trading.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.dto.event.BuyExecutedPayload;
import com.sparta.trading.application.dto.event.MarketContextPayload;
import com.sparta.trading.application.dto.event.TradingEventEnvelope;
import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.entity.OrderRejectReason;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderStatus;
import com.sparta.trading.domain.entity.OrderType;
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.Positions;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingCommandService {

    private final QuoteReader quoteReader;
    private final StocksRepository stocksRepository;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final ExecutionRepository executionRepository;
    private final CashLedgerRepository cashLedgerRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * BUY 시장가 주문의 모든 영속 변경은 이 트랜잭션에서 완료한다.
     * 계좌 행 잠금은 같은 예수금을 사용하는 주문을 직렬화하며, requestId 재확인은 대기 중인 재시도를 중복 처리하지 않는다.
     */
    @Transactional
    public OrderResponse placeOrder(UUID userId, PlaceOrderCommand command) {
        NormalizedOrder normalized = NormalizedOrder.from(command);

        Optional<Orders> existingOrder = orderRepository.findByRequestId(normalized.requestId());
        if (existingOrder.isPresent()) {
            return existingResponse(existingOrder.get(), normalized, resolveStockId(normalized.symbol()), userId);
        }

        validateSupportedOrder(normalized);
        Quote quote = quoteReader.read(normalized.symbol());
        Accounts account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        // 첫 조회와 계좌 잠금 사이에 같은 requestId 주문이 커밋됐을 수 있다.
        existingOrder = orderRepository.findByRequestId(normalized.requestId());
        if (existingOrder.isPresent()) {
            return existingResponse(existingOrder.get(), normalized, quote.stockId(), account);
        }

        validateQuote(quote, normalized.symbol());

        if (!hasLearningMarketContext(quote)) {
            return reject(account, normalized, quote, OrderRejectReason.MARKET_CONTEXT_UNAVAILABLE);
        }

        BigDecimal executionAmount = money(quote.price().multiply(BigDecimal.valueOf(normalized.quantity())));
        if (account.getCashBalance().compareTo(executionAmount) < 0) {
            return reject(account, normalized, quote, OrderRejectReason.INSUFFICIENT_CASH);
        }

        BigDecimal cashBalanceAfter = account.withdraw(executionAmount);

        Optional<Positions> existingPosition = positionRepository
                .findOpenByAccountIdAndStockIdForUpdate(account.getId(), quote.stockId());
        boolean isNewPosition = existingPosition.isEmpty();
        Positions position;
        if (isNewPosition) {
            position = positionRepository.save(Positions.open(
                    account.getId(), quote.stockId(), normalized.quantity(), quote.price(),
                    normalized.plannedStopLossPrice(), normalized.investmentReason(),
                    quote.marketTime(), quote.seq(), userId
            ));
        } else {
            position = existingPosition.get();
            position.buy(normalized.quantity(), quote.price(), userId);
        }

        Orders order = orderRepository.save(Orders.filled(
                normalized.requestId(), account.getId(), quote.stockId(), position.getId(),
                normalized.quantity(), normalized.plannedStopLossPrice(), normalized.investmentReason(),
                quote.marketTime(), quote.seq(), userId
        ));
        Executions execution = executionRepository.save(Executions.buy(
                order.getId(), position.getId(), userId, quote.stockId(), normalized.quantity(),
                quote.price(), position.getAverageEntryPrice(), quote.seq(), quote.marketTime()
        ));
        cashLedgerRepository.save(com.sparta.trading.domain.entity.CashLedgers.buy(
                account, execution.getId(), executionAmount, cashBalanceAfter
        ));

        UUID eventId = UUID.randomUUID();
        outboxEventRepository.save(OutboxEvents.buyExecuted(
                eventId, execution.getId(), userId,
                objectMapper.valueToTree(toBuyEvent(eventId, userId, order, execution, position, quote, isNewPosition)),
                execution.getMarketTime()
        ));

        return response(order, execution, cashBalanceAfter);
    }

    private OrderResponse reject(Accounts account, NormalizedOrder normalized, Quote quote,
                                 OrderRejectReason rejectReason) {
        Orders order = orderRepository.save(Orders.rejected(
                normalized.requestId(), account.getId(), quote.stockId(),
                normalized.quantity(), normalized.plannedStopLossPrice(), normalized.investmentReason(),
                quote.marketTime(), quote.seq(), rejectReason, account.getUserId()
        ));
        return response(order, null, account.getCashBalance());
    }

    private OrderResponse existingResponse(Orders order, NormalizedOrder normalized, Long stockId, UUID userId) {
        Accounts account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT));
        return existingResponse(order, normalized, stockId, account);
    }

    private OrderResponse existingResponse(Orders order, NormalizedOrder normalized, Long stockId, Accounts account) {
        if (!order.belongsTo(account.getId()) || stockId == null || !order.matches(
                stockId, normalized.side(), normalized.orderType(), normalized.quantity(),
                normalized.plannedStopLossPrice(), normalized.investmentReason())) {
            throw new CustomException(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT);
        }

        Executions execution = OrderStatus.FILLED.name().equals(order.getStatus())
                ? executionRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_INCONSISTENT_STATE))
                : null;
        return response(order, execution, account.getCashBalance());
    }

    private Long resolveStockId(String symbol) {
        return stocksRepository.findBySymbol(symbol)
                .map(stock -> stock.getId())
                .orElse(null);
    }

    private void validateSupportedOrder(NormalizedOrder normalized) {
        if (normalized.side() != OrderSide.BUY) {
            throw new CustomException(TradingErrorCode.ORDER_SIDE_NOT_SUPPORTED);
        }
        if (normalized.orderType() != OrderType.MARKET) {
            throw new CustomException(TradingErrorCode.ORDER_TYPE_NOT_SUPPORTED);
        }
    }

    private void validateQuote(Quote quote, String expectedSymbol) {
        if (quote == null || quote.stockId() == null || quote.stockName() == null || quote.stockName().isBlank()
                || quote.price() == null || quote.price().signum() <= 0 || quote.seq() == null
                || quote.marketTime() == null || !expectedSymbol.equals(quote.symbol())) {
            throw new CustomException(TradingErrorCode.ORDER_QUOTE_INVALID);
        }
    }

    private boolean hasLearningMarketContext(Quote quote) {
        return isPositive(quote.recent20dHigh())
                && isPositive(quote.recent20dLow())
                && quote.recent5dReturn() != null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private TradingEventEnvelope toBuyEvent(UUID eventId, UUID userId, Orders order,
                                             Executions execution, Positions position, Quote quote,
                                             boolean isNewPosition) {
        OffsetDateTime executedAt = utc(execution.getMarketTime());
        return new TradingEventEnvelope(
                eventId,
                "BUY_EXECUTED",
                1,
                executedAt,
                userId,
                new BuyExecutedPayload(
                        execution.getId(),
                        order.getId(),
                        position.getId(),
                        isNewPosition,
                        quote.stockId(),
                        quote.symbol(),
                        quote.stockName(),
                        execution.getExecutedQuantity(),
                        execution.getExecutedPrice(),
                        position.getQuantity(),
                        position.getAverageEntryPrice(),
                        position.getPlannedStopLossPrice(),
                        position.getInvestmentReason(),
                        new MarketContextPayload(
                                quote.recent20dHigh(),
                                quote.recent20dLow(),
                                quote.recent5dReturn(),
                                utc(quote.marketTime())
                        ),
                        executedAt
                )
        );
    }

    private OrderResponse response(Orders order, Executions execution, BigDecimal cashBalance) {
        OrderResponse.ExecutionResponse executionResponse = execution == null ? null
                : new OrderResponse.ExecutionResponse(
                execution.getId(), execution.getExecutedQuantity(), execution.getExecutedPrice(),
                execution.getExecutedAmount(), execution.getMarketTime()
        );
        return new OrderResponse(
                order.getId(),
                order.getRequestId(),
                order.getPositionId(),
                OrderStatus.valueOf(order.getStatus()),
                order.getRejectReason(),
                executionResponse,
                cashBalance,
                order.getMarketTime(),
                order.getCandleSeq()
        );
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    private record NormalizedOrder(UUID requestId, String symbol, OrderSide side, OrderType orderType,
                                   int quantity, BigDecimal plannedStopLossPrice, String investmentReason) {
        private static NormalizedOrder from(PlaceOrderCommand command) {
            String symbol = command.symbol().trim().toUpperCase(Locale.ROOT);
            BigDecimal stopLoss = command.plannedStopLossPrice() == null
                    ? null : money(command.plannedStopLossPrice());
            String investmentReason = command.investmentReason() == null || command.investmentReason().isBlank()
                    ? null : command.investmentReason().trim();
            return new NormalizedOrder(command.requestId(), symbol, command.side(), command.orderType(),
                    command.quantity(), stopLoss, investmentReason);
        }
    }
}
