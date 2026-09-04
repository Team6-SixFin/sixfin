package com.sparta.trading.domain.entity;

import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_executions", schema = "trading_service")
public class Executions extends BaseEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "executed_quantity", nullable = false)
    private int executedQuantity;

    @Column(name = "executed_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "executed_amount", nullable = false, precision = 19, scale = 4, insertable = false, updatable = false)
    private BigDecimal executedAmount;

    @Column(name = "avg_entry_price_at_execution", precision = 19, scale = 4)
    private BigDecimal avgEntryPriceAtExecution;

    @Column(name = "realized_profit", precision = 19, scale = 4)
    private BigDecimal realizedProfit;

    @Column(name = "candle_seq", nullable = false)
    private Long candleSeq;

    @Column(name = "market_time", nullable = false)
    private Instant marketTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Executions(UUID orderId, UUID positionId, UUID userId, Long stockId, int executedQuantity,
                       BigDecimal executedPrice, BigDecimal avgEntryPriceAtExecution,
                       Long candleSeq, Instant marketTime) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.positionId = positionId;
        this.userId = userId;
        this.stockId = stockId;
        this.side = OrderSide.BUY.name();
        this.executedQuantity = executedQuantity;
        this.executedPrice = executedPrice.setScale(4, RoundingMode.HALF_UP);
        this.executedAmount = this.executedPrice.multiply(BigDecimal.valueOf(executedQuantity))
                .setScale(4, RoundingMode.HALF_UP);
        this.avgEntryPriceAtExecution = avgEntryPriceAtExecution == null ? null
                : avgEntryPriceAtExecution.setScale(4, RoundingMode.HALF_UP);
        this.candleSeq = candleSeq;
        this.marketTime = marketTime;
    }

    public static Executions buy(UUID orderId, UUID positionId, UUID userId, Long stockId, int executedQuantity,
                                 BigDecimal executedPrice, BigDecimal avgEntryPriceAtExecution,
                                 Long candleSeq, Instant marketTime) {
        return new Executions(orderId, positionId, userId, stockId, executedQuantity, executedPrice,
                avgEntryPriceAtExecution, candleSeq, marketTime);
    }
}
