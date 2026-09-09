package com.sparta.trading.domain.entity;

import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_positions", schema = "trading_service")
public class Positions extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "average_entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageEntryPrice;

    @Column(name = "planned_stop_loss_price", precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    @Column(name = "investment_reason", length = 500)
    private String investmentReason;

    @Column(name = "total_buy_quantity", nullable = false)
    private int totalBuyQuantity;

    @Column(name = "total_buy_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBuyAmount;

    @Column(name = "total_sell_quantity", nullable = false)
    private int totalSellQuantity;

    @Column(name = "total_sell_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalSellAmount;

    @Column(name = "realized_profit", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedProfit;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_seq", nullable = false)
    private Long openedSeq;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_seq")
    private Long closedSeq;

    private Positions(UUID accountId, Long stockId, int quantity, BigDecimal entryPrice,
                      BigDecimal plannedStopLossPrice, String investmentReason,
                      Instant openedAt, Long openedSeq, UUID userId) {
        this.id = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.stockId = Objects.requireNonNull(stockId, "stockId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.status = PositionStatus.OPEN.name();
        this.quantity = quantity;
        this.averageEntryPrice = money(entryPrice);
        this.plannedStopLossPrice = plannedStopLossPrice == null ? null : money(plannedStopLossPrice);
        this.investmentReason = investmentReason;
        this.totalBuyQuantity = quantity;
        this.totalBuyAmount = amount(entryPrice, quantity);
        this.totalSellQuantity = 0;
        this.totalSellAmount = BigDecimal.ZERO.setScale(4);
        this.realizedProfit = BigDecimal.ZERO.setScale(4);
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.openedSeq = Objects.requireNonNull(openedSeq, "openedSeq must not be null");
        initializeAudit(userId);
    }

    public static Positions open(UUID accountId, Long stockId, int quantity, BigDecimal entryPrice,
                                 BigDecimal plannedStopLossPrice, String investmentReason,
                                 Instant openedAt, Long openedSeq, UUID userId) {
        return new Positions(accountId, stockId, quantity, entryPrice, plannedStopLossPrice,
                investmentReason, openedAt, openedSeq, userId);
    }

    /** 기존에 보유 중인 포지션에 추가 매수 내역을 반영한다. */
    public void buy(int buyQuantity, BigDecimal executionPrice, UUID userId) {
        // 이미 종료된 포지션인 경우 매수 불가 예외 처리
        if (!PositionStatus.OPEN.name().equals(status)) {
            throw new IllegalStateException("cannot buy a closed position");
        }

        // 기존 보유 수량 + 이번 매수 수량
        int nextQuantity = Math.addExact(quantity, buyQuantity);
        // 평균 매입가 계산
        BigDecimal totalCost = averageEntryPrice.multiply(BigDecimal.valueOf(quantity))
                .add(money(executionPrice).multiply(BigDecimal.valueOf(buyQuantity)));
        quantity = nextQuantity; // 현재 보유 수량을 매수 후의 수량으로 변경
        totalBuyQuantity = Math.addExact(totalBuyQuantity, buyQuantity); // 기존 매수 누적 수량 + 이번 매수 수량 반영
        totalBuyAmount = totalBuyAmount.add(amount(executionPrice, buyQuantity)); // 기존 매수 누적 금액 + 이번 매수 금액
        // 새로운 평균 매입가 계산
        averageEntryPrice = totalCost.divide(BigDecimal.valueOf(nextQuantity), 4, RoundingMode.HALF_UP);
        markUpdatedBy(userId);
    }

    /** 현재 포지션에서 요청한 수량만큼 매도할 수 있는지 확인 */
    public boolean canSell(int sellQuantity) {
        return PositionStatus.OPEN.name().equals(status)
                && sellQuantity > 0
                && quantity >= sellQuantity;
    }

    /**
     * 보유 주식을 매도하여 보유 수량·총 매도 수량·총 매도 금액·실현 손익을 갱신하고,
     * 전량 매도라면 포지션을 CLOSED 상태로 변경한다.
     */
    public BigDecimal sell(
            int sellQuantity,
            BigDecimal executionPrice,
            Instant closedAt,
            Long closedSeq,
            UUID userId
    ) {
        // 요청한 수량만큼 매도 할 수 있는지 확인
        if (!canSell(sellQuantity)) {
            throw new IllegalArgumentException("insufficient position quantity");
        }

        BigDecimal normalizedExecutionPrice = money(executionPrice);
        // 이번 매도 체결에서 발생한 실현 손익을 계산
        // 실현 손익 = (매도 체결가 - 평균 매입가) × 매도 수량
        BigDecimal executionRealizedProfit = normalizedExecutionPrice
                .subtract(averageEntryPrice)
                .multiply(BigDecimal.valueOf(sellQuantity))
                .setScale(4, RoundingMode.HALF_UP);

        quantity -= sellQuantity;
        totalSellQuantity = Math.addExact(totalSellQuantity, sellQuantity);
        totalSellAmount = totalSellAmount.add(amount(normalizedExecutionPrice, sellQuantity));
        realizedProfit = realizedProfit.add(executionRealizedProfit).setScale(4, RoundingMode.HALF_UP);

        if (quantity == 0) {
            status = PositionStatus.CLOSED.name();
            this.closedAt = Objects.requireNonNull(closedAt, "closedAt must not be null");
            this.closedSeq = Objects.requireNonNull(closedSeq, "closedSeq must not be null");
        }

        markUpdatedBy(userId);
        return executionRealizedProfit; // 이번 매도 체결에서 발생한 실현 손익
    }

    /** 포지션이 종료(CLOSED) 상태인지 확인한다. */
    public boolean isClosed() {
        return PositionStatus.CLOSED.name().equals(status);
    }

    public BigDecimal getAverageExitPrice() {
        if (totalSellQuantity == 0) {
            throw new IllegalStateException("average exit price is unavailable before a sale");
        }
        return totalSellAmount.divide(BigDecimal.valueOf(totalSellQuantity), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal getRealizedReturnRate() {
        if (totalBuyAmount.signum() == 0) {
            throw new IllegalStateException("realized return rate is unavailable without a buy amount");
        }
        return realizedProfit.multiply(BigDecimal.valueOf(100))
                .divide(totalBuyAmount, 4, RoundingMode.HALF_UP);
    }
      
    /** 관리자 계좌 초기화 등으로 포지션을 강제 종료한다. 손익 정산 없이 상태만 닫는다. */
    public void close(Instant closedAt, UUID userId) {
        this.status = PositionStatus.CLOSED.name();
        this.closedAt = closedAt;
        markUpdatedBy(userId);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal price, int quantity) {
        return money(price).multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }
}
