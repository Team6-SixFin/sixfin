package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.port.Quote;
import com.sparta.trading.application.port.QuoteReader;
import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.ClockStatus;
import com.sparta.trading.domain.entity.Executions;
import com.sparta.trading.domain.entity.OrderRejectReason;
import com.sparta.trading.domain.entity.Orders;
import com.sparta.trading.domain.entity.OrderSide;
import com.sparta.trading.domain.entity.OrderStatus;
import com.sparta.trading.domain.entity.OrderType;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.executions.ExecutionsQueryRepository;
import com.sparta.trading.domain.repository.orders.OrdersCommandRepository;
import com.sparta.trading.domain.repository.orders.OrdersQueryRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingCommandService {

    private final QuoteReader quoteReader;
    private final StocksRepository stocksRepository;
    private final AccountsQueryRepository accountsQueryRepository;
    private final OrdersCommandRepository ordersCommandRepository;
    private final OrdersQueryRepository ordersQueryRepository;
    private final ExecutionsQueryRepository executionsQueryRepository;
    private final OrderExecutionService orderExecutionService;

    // 주문 요청을 받아 주문 중복-유형-시세를 검증하고, 거절 또는 정상 주문 처리 경로로 분기
    public OrderResponse placeOrder(UUID userId, PlaceOrderCommand command) {
        // 사용자가 입력한 주문 정보를 실제 주문 처리에 사용하기 좋게 정리
        NormalizedOrder normalized = NormalizedOrder.from(command);

        // 계좌 ID와 requestId를 함께 사용해 기존 주문을 조회한다.
        // 다른 계좌의 같은 requestId는 충돌하지 않는다.
        Optional<UUID> existingRequestAccountId = accountsQueryRepository.findIdByUserId(userId);
        Optional<Orders> existingOrder = existingRequestAccountId.flatMap(accountId ->
                ordersQueryRepository.findByAccountIdAndRequestId(accountId, normalized.requestId())
        );
        // 같은 계좌의 기존 주문이면 새 주문을 만들지 않고 기존 결과를 반환한다.
        if (existingOrder.isPresent()) {
            // 기존 주문 응답용 계좌 조회
            // accountId 조회 직후 계좌가 사라지는 비정상 상태면 requestId 충돌로 처리
            Accounts account = accountsQueryRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT));
            return existingResponse(
                    existingOrder.get(),
                    normalized,
                    resolveStockId(normalized.symbol()),
                    account
            );
        }
        // 현재 MVP에서는 MARKET 주문만 허용한다
        validateSupportedOrder(normalized);
        // 현재 시세 스냅샷 조회
        Quote quote = quoteReader.read(normalized.symbol());
        // 실패할 주문이 계좌 비관적 락을 잡고 다른 정상 주문을 기다리게 할 필요가 없으므로, 락 획득 전에 검증한다.
        // 조회한 종목·시세 정보가 정상이고 현재 주문 가능한 상태인지 검증
        validateQuote(quote, normalized.symbol());

        // Learning에 필요한 시세 정보가 부족한 경우 Reject로 반환
        if (normalized.side() == OrderSide.BUY
                && !hasLearningMarketContext(quote)
                && existingRequestAccountId.isPresent()) {
            return rejectMarketContextWithoutAccountLock(
                    userId,
                    existingRequestAccountId.get(),
                    normalized,
                    quote
            );
        }

        OrderExecutionService.OrderExecutionResult result = orderExecutionService.execute(userId, normalized, quote);
        return response(result.order(), result.execution(), result.cashBalance());
    }

    private OrderResponse rejectMarketContextWithoutAccountLock(
            UUID userId,
            UUID accountId,
            NormalizedOrder normalized,
            Quote quote
    ) {
        try {
            // REJECTED 주문은 잔액·포지션을 바꾸지 않는다. 따라서 계좌 PESSIMISTIC_WRITE 락 없이 짧은 트랜잭션으로 기록한다.
            Orders rejectedOrder = ordersCommandRepository.saveAndFlush(Orders.rejected(
                    normalized.side(), normalized.requestId(), accountId, quote.stockId(),
                    normalized.quantity(), normalized.plannedStopLossPrice(), normalized.investmentReason(),
                    quote.marketTime(), quote.seq(), OrderRejectReason.MARKET_CONTEXT_UNAVAILABLE, userId
            ));
            // 응답에 현재 예수금을 포함해야 하므로 읽기 조회만 한다. 쓰기 락은 걸지 않는다.
            Accounts account = accountsQueryRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));
            return response(rejectedOrder, null, account.getCashBalance());
        } catch (DataIntegrityViolationException exception) {
            // 같은 계좌에서 같은 requestId 요청이 동시에 들어오면 DB의 UNIQUE(account_id, request_id) 제약이 하나만 저장하도록 보장
            // 먼저 처리된 주문을 다시 반환해 멱등성을 유지
            Orders existingOrder = ordersQueryRepository.findByAccountIdAndRequestId(accountId, normalized.requestId())
                    .orElseThrow(() -> exception);
            Accounts account = accountsQueryRepository.findByUserId(userId)
                    .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));
            return existingResponse(existingOrder, normalized, quote.stockId(), account);
        }
    }

    /** 기존 주문 처리 (검증 및 응답) */
    private OrderResponse existingResponse(
            Orders order, // 기존 주문
            NormalizedOrder normalized,
            Long stockId,
            Accounts account // 현재 요청자의 계좌
    ) {
        // 기존 주문의 계좌, 종목, 주문 내용이 현재 요청과 다르면 requestId 충돌 예외 처리
        if (!order.belongsTo(account.getId()) || stockId == null || !order.matches(
                stockId, normalized.side(), normalized.orderType(), normalized.quantity(),
                normalized.plannedStopLossPrice(), normalized.investmentReason())
        ) {
            throw new CustomException(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT);
        }

        // 기존 주문이 체결완료(FILLED) 상태인데 체결 데이터가 없으면 예외 처리 (있으면 execution에 저장)
        Executions execution = OrderStatus.FILLED.name().equals(order.getStatus())
                ? executionsQueryRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_INCONSISTENT_STATE))
                : null; // 체결완료(FILLED) 상태가 아닌 경우 null
        return response(order, execution, account.getCashBalance());
    }

    /**  symbol로 종목을 찾아 해당 종목의 StockId 반환 */
    private Long resolveStockId(String symbol) {
        return stocksRepository.findBySymbol(symbol)
                .map(stock -> stock.getId())
                .orElse(null);
    }

    /** 주문 유형이 시장가(MARKET)가 아니면 지원하지 않는 주문으로 예외 처리 */
    private void validateSupportedOrder(NormalizedOrder normalized) {
        if (normalized.orderType() != OrderType.MARKET) {
            throw new CustomException(TradingErrorCode.ORDER_TYPE_NOT_SUPPORTED);
        }
    }

    /** 현재 시세의 필수 정보와 주문 종목 일치 여부를 검증하고, 시장이 운영 중인지 확인 */
    private void validateQuote(Quote quote, String expectedSymbol) {
        if (quote == null || quote.stockId() == null || quote.stockName() == null || quote.stockName().isBlank()
                || quote.price() == null || quote.price().signum() <= 0 || quote.seq() == null
                || quote.marketTime() == null || quote.clockStatus() == null
                || !expectedSymbol.equals(quote.symbol())) {
            throw new CustomException(TradingErrorCode.ORDER_QUOTE_INVALID);
        }

        // 시장 재생이 정지된 상태라면 에러 처리로 주문 막음
        if (quote.clockStatus() != ClockStatus.RUNNING) {
            throw new CustomException(TradingErrorCode.MARKET_STOPPED);
        }
    }

    /** 학습용 시장 판단에 필요한 시세 정보가 모두 있는지 확인한다. */
    private boolean hasLearningMarketContext(Quote quote) {
        // 최근 20일 고가와 저가는 0보다 큰 값, 최근 5일 수익률은 NotNull이어야 함 (true, false로 반환)
        return isPositive(quote.recent20dHigh())
                && isPositive(quote.recent20dLow())
                && quote.recent5dReturn() != null;
    }

    /** 값이 null이 아니고 0보다 큰지 확인 */
    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** 저장된 주문 엔티티와 체결 엔티티를 API 응답 DTO로 변환한다. */
    private OrderResponse response(
            Orders order,
            Executions execution,
            BigDecimal cashBalance
    ) {
        // 체결 정보가 있는 경우에만 API 응답용 ExecutionResponse를 만든다.
        OrderResponse.ExecutionResponse executionResponse = execution == null
                ? null
                : new OrderResponse.ExecutionResponse(
                execution.getId(), execution.getExecutedPrice(), execution.getExecutedQuantity(),
                execution.getExecutedAmount(), execution.getRealizedProfit()
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

}
