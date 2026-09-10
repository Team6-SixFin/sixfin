package com.sparta.trading.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.trading.application.dto.command.PlaceOrderCommand;
import com.sparta.trading.application.dto.event.BuyExecutedPayload;
import com.sparta.trading.application.dto.event.MarketContextPayload;
import com.sparta.trading.application.dto.event.PositionClosedPayload;
import com.sparta.trading.application.dto.event.SellExecutedPayload;
import com.sparta.trading.application.dto.event.TradingEventEnvelope;
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
import com.sparta.trading.domain.entity.OutboxEvents;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.executions.ExecutionsRepository;
import com.sparta.trading.domain.repository.orders.OrdersRepository;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsRepository;
import com.sparta.trading.domain.repository.positions.PositionsRepository;
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
    private final AccountsCommandRepository accountsCommandRepository;
    private final AccountsQueryRepository accountsQueryRepository;
    private final OrdersRepository orderRepository;
    private final PositionsRepository positionRepository;
    private final ExecutionsRepository executionRepository;
    private final CashLedgersCommandRepository cashLedgerRepository;
    private final OutboxEventsRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // 주문 요청을 받아 중복 주문, 주문 유형, 시세, 계좌를 확인한 후 매수 또는 매도를 처리
    @Transactional
    public OrderResponse placeOrder(UUID userId, PlaceOrderCommand command) {
        // 사용자가 입력한 주문 정보를 실제 주문 처리에 사용하기 좋게 정리
        NormalizedOrder normalized = NormalizedOrder.from(command);

        // requestId로 기존 주문 조회
        Optional<Orders> existingOrder = orderRepository.findByRequestId(normalized.requestId());
        // 기존 주문이 있으면 새 주문을 생성하지 않고 기존 주문을 검증하여 처리
        if (existingOrder.isPresent()) {
            return existingResponse(
                    existingOrder.get(), // 기존 주문
                    normalized, // 이번 요청 주문 내용
                    resolveStockId(normalized.symbol()), // 이번 요청의 StockId
                    userId // 요청한 userId
            );
        }
        // 현재 MVP에서는 MARKET 주문만 허용한다
        validateSupportedOrder(normalized);
        // 현재 시세 스냅샷 조회
        Quote quote = quoteReader.read(normalized.symbol());
        // 계좌 행을 비관적 락으로 조회
        Accounts account = accountsCommandRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ACCOUNT_NOT_FOUND));

        // 첫 조회와 계좌 잠금 사이에 같은 requestId 주문이 커밋됐을 수 있다.
        // 계좌 잠금 대기 중 같은 requestId의 주문이 생성됐는지 다시 확인
        existingOrder = orderRepository.findByRequestId(normalized.requestId());
        if (existingOrder.isPresent()) {
            return existingResponse(existingOrder.get(), normalized, quote.stockId(), account);
        }

        // 조회한 종목·시세 정보가 정상이고 현재 주문 가능한 상태인지 검증
        validateQuote(quote, normalized.symbol());

        // 주문이 매수(BUY)인지 매도(SELL)인지에 따라 처리 구분
        if (normalized.side() == OrderSide.BUY) {
            return executeBuy(userId, normalized, quote, account);
        }
        return executeSell(userId, normalized, quote, account);

    }

    /** 매수 체결 처리 */
    /**
     * 시세·지표 검증 → 예수금 검증 및 차감 → 포지션 생성 또는 추가 매수 → 주문 저장 → 체결 저장
     * → 현금 원장 저장 → BUY_EXECUTED Outbox 저장 → 응답 반환
     */
    private OrderResponse executeBuy(
            UUID userId, NormalizedOrder normalized, Quote quote, Accounts account
    ) {
        // Learning에 필요한 시세 정보가 부족한 경우 Reject로 반환
        if (!hasLearningMarketContext(quote)) {
            return reject(account, normalized, quote, OrderRejectReason.MARKET_CONTEXT_UNAVAILABLE);
        }

        // 실제 매수에 필요한 금액 계산 (현재 시세 X 수량)
        BigDecimal executionAmount = executionAmount(quote, normalized.quantity());
        // 사용가능 예수금이 매수에 필요한 금액보다 작은 경우 Reject (금액 부족)
        if (account.getCashBalance().compareTo(executionAmount) < 0) {
            return reject(account, normalized, quote, OrderRejectReason.INSUFFICIENT_CASH);
        }

        // 예수금에서 매수 금액 차감
        BigDecimal cashBalanceAfter = account.withdraw(executionAmount);

        // Open 상태의 포지션만 조회
        Optional<Positions> existingPosition = positionRepository
                .findOpenByAccountIdAndStockIdForUpdate(account.getId(), quote.stockId());
        boolean isNewPosition = existingPosition.isEmpty(); // 포지션이 없다면 이번 매수는 최초 매수
        Positions position;
        // 최초 매수인 경우 새 포지션 생성
        if (isNewPosition) {
            position = positionRepository.save(Positions.open(
                    account.getId(), quote.stockId(), normalized.quantity(), quote.price(),
                    normalized.plannedStopLossPrice(), normalized.investmentReason(),
                    quote.marketTime(), quote.seq(), userId
            ));
        } else {
            // 기존 포지션이 있다면 추가 매수
            position = existingPosition.get();
            // 보유 수량, 총 매수 수량, 총 매수 금액, 평균 매입가 갱신
            position.buy(normalized.quantity(), quote.price(), userId);
        }

        // 주문 자체를 FILLED 상태로 저장 (order는 사용자가 매수를 요청한 기록임)
        Orders order = orderRepository.save(Orders.filled(
                OrderSide.BUY,
                normalized.requestId(), account.getId(), quote.stockId(), position.getId(),
                normalized.quantity(), normalized.plannedStopLossPrice(), normalized.investmentReason(),
                quote.marketTime(), quote.seq(), userId
        ));
        // 주문이 실제로 체결되었다는 이력을 저장
        Executions execution = executionRepository.save(Executions.buy(
                order.getId(), position.getId(), userId, quote.stockId(), normalized.quantity(),
                quote.price(), position.getAverageEntryPrice(), quote.seq(), quote.marketTime()
        ));
        // 현금 원장을 저장한다.
        cashLedgerRepository.save(com.sparta.trading.domain.entity.CashLedgers.buy(
                account, execution.getId(), executionAmount, cashBalanceAfter
        ));

        // Outbox 이벤트의 고유 ID 생성
        UUID eventId = UUID.randomUUID();
        // 매수 체결 완료 이벤트를 Outbox 테이블에 저장
        outboxEventRepository.save(OutboxEvents.buyExecuted(
                eventId, execution.getId(), userId,
                // toBuyEvent로 이벤트 DTO 생성후 valueToTree()로 Json 형태로 변환해 Outbox payload에 저장
                objectMapper.valueToTree(toBuyEvent(eventId, userId, order, execution, position, quote, isNewPosition)),
                execution.getMarketTime()
        ));

        return response(order, execution, cashBalanceAfter);
    }

    /** 매도 체결 처리 */
    /**
     * 보유 포지션·수량 검증 → 포지션 수량 차감 및 실현 손익 계산 → 예수금 입금
     * → 주문 저장 → 체결 저장 → 현금 원장 저장 → SELL_EXECUTED Outbox 저장
     * → 전량 매도 시 POSITION_CLOSED Outbox 저장 → 응답 반환
     */
    private OrderResponse executeSell(UUID userId, NormalizedOrder normalized, Quote quote, Accounts account) {
        // 해당 계좌가 현재 보유 중인 OPEN 포지션을 조회
        Positions position = positionRepository
                .findOpenByAccountIdAndStockIdForUpdate(account.getId(), quote.stockId())
                .orElse(null);

        // 매도 가능한 OPEN 포지션이 없거나 요청한 매도 수량이 현재 보유 수량보다 크면 주문을 거절
        if (position == null || !position.canSell(normalized.quantity())) {
            return reject(account, normalized, quote, OrderRejectReason.INSUFFICIENT_POSITION_QUANTITY);
        }

        // 매도 체결 금액 계산
        BigDecimal executionAmount = executionAmount(quote, normalized.quantity());
        // 체결 당시 평균 매입가 스냅샷
        BigDecimal averageEntryPrice = position.getAverageEntryPrice();
        // 현재 포지션의 상태와 값들을 실제로 매도 결과에 맞게 변경
        BigDecimal executionRealizedProfit = position.sell(
                normalized.quantity(), quote.price(), quote.marketTime(), quote.seq(), userId
        );
        BigDecimal cashBalanceAfter = account.deposit(executionAmount); // 매도 후 예수금 (예수금 증가)

        // 사용자의 매도 요청 자체를 FILLED 주문으로 저장
        Orders order = orderRepository.save(Orders.filled(
                OrderSide.SELL,
                normalized.requestId(), account.getId(), quote.stockId(), position.getId(),
                normalized.quantity(), null, null, quote.marketTime(), quote.seq(), userId
        ));
        // 주문이 실제 체결된 상세 이력을 저장
        Executions execution = executionRepository.save(Executions.sell(
                order.getId(), position.getId(), userId, quote.stockId(), normalized.quantity(),
                quote.price(), averageEntryPrice, executionRealizedProfit, quote.seq(), quote.marketTime()
        ));
        // 현금 원장을 저장
        cashLedgerRepository.save(com.sparta.trading.domain.entity.CashLedgers.sell(
                account, execution.getId(), executionAmount, cashBalanceAfter
        ));

        UUID sellEventId = UUID.randomUUID();
        outboxEventRepository.save(OutboxEvents.sellExecuted(
                sellEventId,
                execution.getId(),
                userId,
                objectMapper.valueToTree(toSellEvent(sellEventId, userId, order, execution, position, quote)),
                execution.getMarketTime()
        ));

        // 포지션이 완전히 종료(CLOSED)된 경우
        if (position.isClosed()) {
            UUID closedEventId = UUID.randomUUID(); // 이벤트 ID 생성
            // 포지션 종료를 outbox 이벤트로 저장
            outboxEventRepository.save(OutboxEvents.positionClosed(
                    closedEventId,
                    position.getId(),
                    userId,
                    // 포지션 종료 정보를 이벤트 DTO로 만든 뒤 JSON payload로 변환
                    objectMapper.valueToTree(toPositionClosedEvent(closedEventId, userId, position, quote)),
                    position.getClosedAt()
            ));
        }

        return response(order, execution, cashBalanceAfter);
    }

    /** 주문을 거절 상태로 저장하고, 거절된 주문 정보를 응답한다. */
    private OrderResponse reject(Accounts account, NormalizedOrder normalized, Quote quote,
                                 OrderRejectReason rejectReason) {
        Orders order = orderRepository.save(Orders.rejected(
                normalized.side(),
                normalized.requestId(), account.getId(), quote.stockId(),
                normalized.quantity(), normalized.plannedStopLossPrice(), normalized.investmentReason(),
                quote.marketTime(), quote.seq(), rejectReason, account.getUserId()
        ));
        return response(order, null, account.getCashBalance());
    }

    /** 기존 주문 처리 (계좌 조회) */
    private OrderResponse existingResponse(
            Orders order, // 기존 주문
            NormalizedOrder normalized, // 이번에 들어온 주문 요청 정보
            Long stockId, // 이번에 요청한 StockId
            UUID userId // 이번에 요청한 UserId
    ) {
        // userId로 account(계좌) 조회
        Accounts account = accountsQueryRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(TradingErrorCode.ORDER_REQUEST_ID_CONFLICT));
        // account를 인수에 담아 아래의 existingResponse 호출
        return existingResponse(order, normalized, stockId, account);
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
                ? executionRepository.findByOrderId(order.getId())
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

    /** 아래의 이벤트 메서드들은 이벤트 객체만 생성하며, 실제 Kafka 발행은 Outbox Publisher가 담당한다. */

    /** 매수 주문이 체결된 뒤 Kafka로 전달할 매수 체결 이벤트를 생성한다.*/
    private TradingEventEnvelope toBuyEvent(
            UUID eventId, UUID userId, Orders order,
            Executions execution, Positions position, Quote quote,
            boolean isNewPosition
    ) {
        OffsetDateTime executedAt = utc(execution.getMarketTime());
        // Kafka 이벤트 전체 묶음
        return new TradingEventEnvelope(
                eventId,
                "BUY_EXECUTED",
                1,
                executedAt,
                userId,
                // 매수 체결 상세 정보
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
                        // 체결 당시 시장 정보
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

    /** 매도 주문이 체결된 뒤 Kafka로 전달할 매도 체결 이벤트를 생성한다. */
    private TradingEventEnvelope toSellEvent(
            UUID eventId, UUID userId, Orders order,
            Executions execution, Positions position, Quote quote
    ) {
        OffsetDateTime executedAt = utc(execution.getMarketTime());
        // Kafka 이벤트 전체 묶음
        return new TradingEventEnvelope(
                eventId,
                "SELL_EXECUTED",
                1,
                executedAt,
                userId,
                // 매도 체결 상세 정보
                new SellExecutedPayload(
                        execution.getId(),
                        order.getId(),
                        position.getId(),
                        quote.stockId(),
                        quote.symbol(),
                        quote.stockName(),
                        execution.getExecutedQuantity(),
                        execution.getExecutedPrice(),
                        position.getQuantity(),
                        position.getAverageEntryPrice(),
                        position.getPlannedStopLossPrice(),
                        execution.getRealizedProfit(),
                        utc(quote.marketTime()),
                        executedAt
                )
        );
    }

    /** 포지션 종료 이벤트 생성: 보유 수량이 모두 매도되어 포지션이 종료된 뒤, Kafka로 전달할 포지션 종료 이벤트를 생성한다. */
    private TradingEventEnvelope toPositionClosedEvent(UUID eventId, UUID userId,
                                                        Positions position, Quote quote) {
        OffsetDateTime closedAt = utc(position.getClosedAt());
        // Kafka 이벤트 전체 묶음
        return new TradingEventEnvelope(
                eventId,
                "POSITION_CLOSED",
                1,
                closedAt,
                userId,
                // 종료된 포지션의 최종 정보
                new PositionClosedPayload(
                        position.getId(),
                        quote.stockId(),
                        quote.symbol(),
                        quote.stockName(),
                        position.getTotalBuyQuantity(),
                        position.getAverageEntryPrice(),
                        position.getAverageExitPrice(),
                        position.getPlannedStopLossPrice(),
                        position.getRealizedProfit(),
                        position.getRealizedReturnRate(),
                        utc(position.getOpenedAt()),
                        closedAt
                )
        );
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

    /** Instant 시간을 UTC 기준의 OffsetDateTime으로 변환 */
    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
    /** 금액을 소수점 넷째 자리까지 반올림 */
    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.HALF_UP);
    }
    /** 현재 종목 가격과 주문 수량을 곱해 체결 금액을 계산 */
    private static BigDecimal executionAmount(Quote quote, int quantity) {
        return money(quote.price().multiply(BigDecimal.valueOf(quantity)));
    }

    /** 사용자가 입력한 주문 정보를 확인하고 정리해서 실제 주문 처리에 사용할 주문 정보로 만듬 */
    private record NormalizedOrder(
            UUID requestId, String symbol, OrderSide side, OrderType orderType,
            int quantity, BigDecimal plannedStopLossPrice, String investmentReason
    ) {
        private static NormalizedOrder from(PlaceOrderCommand command) {
            // symbol 비교와 시세 조회가 안정적으로 동작하도록 공백을 제거하고 대문자화한다.
            String symbol = command.symbol().trim().toUpperCase(Locale.ROOT);
            boolean isBuy = command.side() == OrderSide.BUY;

            // 매수 주문에 손절가가 있으면 금액 형식을 맞추고, 매도이거나 손절가가 없으면 null로 처리
            BigDecimal stopLoss = !isBuy || command.plannedStopLossPrice() == null
                    ? null : money(command.plannedStopLossPrice());

            // 매수 주문에 투자 이유가 있으면 앞뒤 공백을 제거하고, 매도이거나 투자 이유가 없으면 null로 처리
            String investmentReason = !isBuy || command.investmentReason() == null || command.investmentReason().isBlank()
                    ? null : command.investmentReason().trim();

            return new NormalizedOrder(command.requestId(), symbol, command.side(), command.orderType(),
                    command.quantity(), stopLoss, investmentReason);
        }
    }
}
