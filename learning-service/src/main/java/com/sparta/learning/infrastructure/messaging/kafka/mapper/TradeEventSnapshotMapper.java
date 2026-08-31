package com.sparta.learning.infrastructure.messaging.kafka.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ConsumedEvent;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.infrastructure.messaging.kafka.dto.BuyExecutedPayload;
import com.sparta.learning.infrastructure.messaging.kafka.dto.PositionClosedPayload;
import com.sparta.learning.infrastructure.messaging.kafka.dto.SellExecutedPayload;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이벤트의 JSON payload를 이벤트 유형별 DTO로 검증한 뒤 조회용 스냅샷으로 변환
 */
@Component
@RequiredArgsConstructor
public class TradeEventSnapshotMapper {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ExecutionSnapshot toBuySnapshot(ConsumedEvent consumedEvent, TradingEventEnvelope event) {
        BuyExecutedPayload payload = readAndValidate(event.payload(), BuyExecutedPayload.class, event);

        return ExecutionSnapshot.builder()
                .consumedEvent(consumedEvent)
                .executionId(payload.executionId())
                .orderId(payload.orderId())
                .positionId(payload.positionId())
                .userId(event.userId())
                .stockId(payload.stockId())
                .stockSymbol(payload.stockCode())
                .stockName(payload.stockName())
                .tradeType(TradeType.BUY)
                .newPosition(payload.newPosition())
                .quantity(payload.quantity())
                .executedPrice(payload.executedPrice())
                .positionQuantityAfter(payload.positionQuantityAfter())
                .positionAveragePrice(payload.positionAverageEntryPrice())
                .plannedStopLossPrice(payload.plannedStopLossPrice())
                .investmentReason(payload.investmentReason())
                .recent20dHigh(payload.marketContext().recent20DayHigh())
                .recent20dLow(payload.marketContext().recent20DayLow())
                .recent5dReturnRate(payload.marketContext().recent5DayReturnRate())
                .quoteAt(payload.marketContext().quoteTimestamp())
                .executedAt(payload.executedAt())
                .build();
    }

    public ExecutionSnapshot toSellSnapshot(ConsumedEvent consumedEvent, TradingEventEnvelope event) {
        SellExecutedPayload payload = readAndValidate(event.payload(), SellExecutedPayload.class, event);

        return ExecutionSnapshot.builder()
                .consumedEvent(consumedEvent)
                .executionId(payload.executionId())
                .orderId(payload.orderId())
                .positionId(payload.positionId())
                .userId(event.userId())
                .stockId(payload.stockId())
                .stockSymbol(payload.stockCode())
                .stockName(payload.stockName())
                .tradeType(TradeType.SELL)
                .newPosition(false)
                .quantity(payload.quantity())
                .executedPrice(payload.executedPrice())
                .positionQuantityAfter(payload.positionQuantityAfter())
                .positionAveragePrice(payload.positionAverageEntryPrice())
                .plannedStopLossPrice(payload.plannedStopLossPrice())
                .executionRealizedProfit(payload.executionRealizedProfit())
                .quoteAt(payload.quoteTimestamp())
                .executedAt(payload.executedAt())
                .build();
    }

    public ClosedPositionSnapshot toClosedPositionSnapshot(
            ConsumedEvent consumedEvent,
            TradingEventEnvelope event
    ) {
        PositionClosedPayload payload = readAndValidate(event.payload(), PositionClosedPayload.class, event);

        return ClosedPositionSnapshot.builder()
                .consumedEvent(consumedEvent)
                .positionId(payload.positionId())
                .userId(event.userId())
                .stockId(payload.stockId())
                .stockSymbol(payload.stockCode())
                .stockName(payload.stockName())
                // 현재 MVP는 전량 매도 후에만 종료 이벤트를 발행하므로 매수·매도 누적 수량이 같습니다.
                .totalBoughtQuantity(payload.totalQuantity())
                .totalSoldQuantity(payload.totalQuantity())
                .averageEntryPrice(payload.averageEntryPrice())
                .averageExitPrice(payload.averageExitPrice())
                .plannedStopLossPrice(payload.stopLossPrice())
                .realizedProfit(payload.realizedProfit())
                .realizedReturnRate(payload.realizedReturnRate())
                .openedAt(payload.openedAt())
                .closedAt(payload.closedAt())
                .build();
    }

    private <T> T readAndValidate(JsonNode payload, Class<T> payloadType, TradingEventEnvelope event) {
        try {
            T mappedPayload = objectMapper.treeToValue(payload, payloadType);
            Set<ConstraintViolation<T>> violations = validator.validate(mappedPayload);

            if (!violations.isEmpty()) {
                String invalidFields = violations.stream()
                        .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                        .sorted()
                        .collect(Collectors.joining(", "));

                throw new InvalidTradeEventException(
                        "유효하지 않은 " + event.eventType() + " payload입니다. eventId="
                                + event.eventId() + ", violations=[" + invalidFields + "]"
                );
            }

            return mappedPayload;
        } catch (JsonProcessingException exception) {
            throw new InvalidTradeEventException(
                    "해석할 수 없는 " + event.eventType() + " payload입니다. eventId=" + event.eventId(),
                    exception
            );
        }
    }
}
