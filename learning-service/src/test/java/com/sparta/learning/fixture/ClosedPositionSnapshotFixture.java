package com.sparta.learning.fixture;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// CLOSE 단계 진단 규칙 테스트용 포지션 종료 스냅샷을 만듭니다
// 손실로 종료한 포지션을 기본값으로 사용합니다
public final class ClosedPositionSnapshotFixture {

    private static final BigDecimal AVERAGE_ENTRY_PRICE = new BigDecimal("183.1700");
    private static final BigDecimal AVERAGE_EXIT_PRICE = new BigDecimal("172.5000");
    private static final BigDecimal STOP_LOSS_PRICE = new BigDecimal("174.0000");
    private static final BigDecimal REALIZED_PROFIT = new BigDecimal("-106.7000");
    private static final BigDecimal REALIZED_RETURN_RATE = new BigDecimal("-5.8300");

    private ClosedPositionSnapshotFixture() {
    }

    // 계획 손절가가 있는 포지션 종료
    public static ClosedPositionSnapshot closedWithStopLoss() {
        return closed(STOP_LOSS_PRICE);
    }

    // 계획 손절가 없이 종료한 포지션. 판정 불가 검증에 사용한다
    public static ClosedPositionSnapshot closedWithoutStopLoss() {
        return closed(null);
    }

    public static ClosedPositionSnapshot closed(BigDecimal plannedStopLossPrice) {
        return ClosedPositionSnapshot.builder()
                .positionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .stockId(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .totalBoughtQuantity(10L)
                .totalSoldQuantity(10L)
                .averageEntryPrice(AVERAGE_ENTRY_PRICE)
                .averageExitPrice(AVERAGE_EXIT_PRICE)
                .plannedStopLossPrice(plannedStopLossPrice)
                .realizedProfit(REALIZED_PROFIT)
                .realizedReturnRate(REALIZED_RETURN_RATE)
                .openedAt(OffsetDateTime.now().minusDays(3))
                .closedAt(OffsetDateTime.now())
                .build();
    }
}
