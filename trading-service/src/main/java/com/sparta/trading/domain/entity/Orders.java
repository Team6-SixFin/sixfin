package com.sparta.trading.domain.entity;

import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_orders", schema = "trading_service")
public class Orders extends BaseEntity {

    @Id
    @Column(name="id")
    private UUID id;

    @Column(name="request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name="account_id")
    private UUID accountId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name="position_id")
    private UUID positionId;

    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType;

    @Column(name="quantity", nullable = false)
    private Integer quantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reject_reason",  length = 100)
    private String rejectReason;

    @Column(name = "planned_stop_loss_price", precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    @Column(name = "investment_reason",  length = 500)
    private String investmentReason;

    @Column(name = "market_time", nullable = false)
    private Instant marketTime;

    @Column(name = "candle_seq", nullable = false)
    private Long candleSeq;

    private Orders(UUID requestId, UUID accountId, Long stockId, UUID positionId,
                   OrderSide side, OrderType orderType, int quantity, OrderStatus status,
                   OrderRejectReason rejectReason, BigDecimal plannedStopLossPrice,
                   String investmentReason, Instant marketTime, Long candleSeq, UUID userId) {
        this.id = UUID.randomUUID();
        this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.stockId = Objects.requireNonNull(stockId, "stockId must not be null");
        this.positionId = positionId;
        this.side = Objects.requireNonNull(side, "side must not be null").name();
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null").name();
        this.quantity = quantity;
        this.status = Objects.requireNonNull(status, "status must not be null").name();
        this.rejectReason = rejectReason == null ? null : rejectReason.name();
        this.plannedStopLossPrice = plannedStopLossPrice;
        this.investmentReason = investmentReason;
        this.marketTime = Objects.requireNonNull(marketTime, "marketTime must not be null");
        this.candleSeq = Objects.requireNonNull(candleSeq, "candleSeq must not be null");
        initializeAudit(userId);
    }

    public static Orders filled(UUID requestId, UUID accountId, Long stockId,
                                UUID positionId, int quantity, BigDecimal plannedStopLossPrice,
                                String investmentReason, Instant marketTime, Long candleSeq,
                                UUID userId) {
        return new Orders(requestId, accountId, stockId, positionId,
                OrderSide.BUY, OrderType.MARKET, quantity, OrderStatus.FILLED, null,
                plannedStopLossPrice, investmentReason, marketTime, candleSeq, userId);
    }

    public static Orders rejected(UUID requestId, UUID accountId, Long stockId,
                                  int quantity, BigDecimal plannedStopLossPrice,
                                  String investmentReason, Instant marketTime, Long candleSeq,
                                  OrderRejectReason rejectReason, UUID userId) {
        return new Orders(requestId, accountId, stockId, null,
                OrderSide.BUY, OrderType.MARKET, quantity, OrderStatus.REJECTED, rejectReason,
                plannedStopLossPrice, investmentReason, marketTime, candleSeq, userId);
    }

    public boolean belongsTo(UUID accountId) {
        return this.accountId.equals(accountId);
    }

    public boolean matches(Long stockId, OrderSide side, OrderType orderType, int quantity,
                           BigDecimal plannedStopLossPrice, String investmentReason) {
        return this.stockId.equals(stockId)
                && this.side.equals(side.name())
                && this.orderType.equals(orderType.name())
                && this.quantity == quantity
                && sameMoney(this.plannedStopLossPrice, plannedStopLossPrice)
                && Objects.equals(this.investmentReason, investmentReason);
    }

    private static boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }
}
