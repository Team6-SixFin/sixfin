package com.sparta.learning.fixture;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

//진단 규칙 테스트용 체결 스냅샷을 만듭니다
// 금액은 샘플 이벤트(buy-executed-first.json)와 맞춤
public final class ExecutionSnapshotFixture {

    private static final BigDecimal EXECUTED_PRICE = new BigDecimal("183.1700");
    private static final BigDecimal STOP_LOSS_PRICE = new BigDecimal("174.0000");
    private static final BigDecimal RECENT_20D_HIGH = new BigDecimal("187.4000");
    private static final BigDecimal RECENT_20D_LOW = new BigDecimal("169.2100");
    private static final BigDecimal RECENT_5D_RETURN_RATE = new BigDecimal("5.2000");

    private ExecutionSnapshotFixture() {
    }

    // 손절가를 설정하고 매수한 최초 체결
    public static ExecutionSnapshot firstBuyWithStopLoss() {
        return firstBuy(STOP_LOSS_PRICE);
    }

    // 손절가 없이 매수한 최초 체결
    public static ExecutionSnapshot firstBuyWithoutStopLoss() {
        return firstBuy(null);
    }

    // 손절가를 직접 지정한 최초 체결. 손절 폭 계산 검증에 사용한다
    public static ExecutionSnapshot firstBuy(BigDecimal plannedStopLossPrice) {
        return builder()
                .tradeType(TradeType.BUY)
                .newPosition(true)
                .plannedStopLossPrice(plannedStopLossPrice)
                .build();
    }

    // 추가 매수 체결(TRADE)
    public static ExecutionSnapshot additionalBuy() {
        return builder()
                .tradeType(TradeType.BUY)
                .newPosition(false)
                .plannedStopLossPrice(STOP_LOSS_PRICE)
                .build();
    }

    // 매도 체결. 최초 매도를 다루지 않기 때문에 newPosition은 false임
    public static ExecutionSnapshot sell() {
        return builder()
                .tradeType(TradeType.SELL)
                .newPosition(false)
                .plannedStopLossPrice(STOP_LOSS_PRICE)
                .build();
    }

    // 매수가를 직접 지정한 최초 체결
    public static ExecutionSnapshot buyAt(BigDecimal executedPrice){
        return builder()
                .tradeType(TradeType.BUY)
                .newPosition(true)
                .plannedStopLossPrice(STOP_LOSS_PRICE)
                .executedPrice(executedPrice)
                .positionAveragePrice(executedPrice)
                .build();
    }

    // 20일 최고가를 직접 지정한 최초 체결
    public static ExecutionSnapshot buyWithRecent20dHigh(BigDecimal recent20dHigh) {
        return builder()
                .tradeType(TradeType.BUY)
                .newPosition(true)
                .plannedStopLossPrice(STOP_LOSS_PRICE)
                .recent20dHigh(recent20dHigh)
                .build();
    }

    // 최근 5거래일 수익률을 직접 지정한 최초 체결. 단기 급등 판정 검증에 사용한다
    public static ExecutionSnapshot buyWithRecent5dReturnRate(BigDecimal recent5dReturnRate) {
        return builder()
                .tradeType(TradeType.BUY)
                .newPosition(true)
                .plannedStopLossPrice(STOP_LOSS_PRICE)
                .recent5dReturnRate(recent5dReturnRate)
                .build();
    }

    // 매도가와 계획 손절가를 직접 지정한 매도 체결
    public static ExecutionSnapshot sellAt(BigDecimal executedPrice, BigDecimal plannedStopLossPrice) {
        return builder()
                .tradeType(TradeType.SELL)
                .newPosition(false)
                .plannedStopLossPrice(plannedStopLossPrice)
                .executedPrice(executedPrice)
                .build();
    }

    //판정에 쓰이지 않는 필드를 기본값으로 채웁니다.
    // 저장까지 검증하는 테스트에서는 별도로 채워야 함 (TODO)
    private static ExecutionSnapshot.ExecutionSnapshotBuilder builder() {
        return ExecutionSnapshot.builder()
                .executionId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .stockId(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .quantity(10)
                .executedPrice(EXECUTED_PRICE)
                .positionQuantityAfter(10)
                .positionAveragePrice(EXECUTED_PRICE)
                .investmentReason("신제품 출시 이후 서비스 매출 성장 기대")
                .recent20dHigh(RECENT_20D_HIGH)
                .recent20dLow(RECENT_20D_LOW)
                .recent5dReturnRate(RECENT_5D_RETURN_RATE)
                .quoteAt(OffsetDateTime.now())
                .executedAt(OffsetDateTime.now());
    }
}