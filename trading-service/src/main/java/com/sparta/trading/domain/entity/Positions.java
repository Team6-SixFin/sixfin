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

    public void buy(int buyQuantity, BigDecimal executionPrice, UUID userId) {
        if (!PositionStatus.OPEN.name().equals(status)) {
            throw new IllegalStateException("cannot buy a closed position");
        }

        int nextQuantity = Math.addExact(quantity, buyQuantity);
        BigDecimal totalCost = averageEntryPrice.multiply(BigDecimal.valueOf(quantity))
                .add(money(executionPrice).multiply(BigDecimal.valueOf(buyQuantity)));
        quantity = nextQuantity;
        totalBuyQuantity = Math.addExact(totalBuyQuantity, buyQuantity);
        totalBuyAmount = totalBuyAmount.add(amount(executionPrice, buyQuantity));
        averageEntryPrice = totalCost.divide(BigDecimal.valueOf(nextQuantity), 4, RoundingMode.HALF_UP);
        markUpdatedBy(userId);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal price, int quantity) {
        return money(price).multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }
}
