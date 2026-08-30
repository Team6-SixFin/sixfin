package com.sparta.learning.domain.entity;

import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "execution_snapshots",
        indexes = {
                @Index(name = "idx_execution_snapshot_position_id", columnList = "position_id"),
                @Index(name = "idx_execution_snapshot_user_id", columnList = "user_id"),
                @Index(name = "idx_execution_snapshot_stock_id", columnList = "stock_id"),
                @Index(name = "idx_execution_snapshot_executed_at", columnList = "executed_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumed_event_id", nullable = false, unique = true)
    private ConsumedEvent consumedEvent;

    @Column(name = "execution_id", nullable = false, unique = true)
    private UUID executionId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 10)
    private TradeType tradeType;

    @Column(name = "is_new_position", nullable = false)
    private boolean newPosition;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "executed_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "position_quantity_after", nullable = false)
    private int positionQuantityAfter;

    @Column(name = "position_average_price", precision = 19, scale = 4)
    private BigDecimal positionAveragePrice;

    @Column(name = "planned_stop_loss_price", precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    @Column(name = "investment_reason", columnDefinition = "text")
    private String investmentReason;

    @Column(name = "recent_20d_high", precision = 19, scale = 4)
    private BigDecimal recent20dHigh;

    @Column(name = "recent_20d_low", precision = 19, scale = 4)
    private BigDecimal recent20dLow;

    @Column(name = "recent_5d_return_rate", precision = 9, scale = 4)
    private BigDecimal recent5dReturnRate;

    @Column(name = "execution_realized_profit", precision = 19, scale = 4)
    private BigDecimal executionRealizedProfit;

    @Column(name = "quote_at", nullable = false)
    private OffsetDateTime quoteAt;

    @Column(name = "executed_at", nullable = false)
    private OffsetDateTime executedAt;

    @Builder
    private ExecutionSnapshot(
            ConsumedEvent consumedEvent,
            UUID executionId,
            UUID orderId,
            UUID positionId,
            UUID userId,
            Long stockId,
            String stockSymbol,
            String stockName,
            TradeType tradeType,
            boolean newPosition,
            int quantity,
            BigDecimal executedPrice,
            int positionQuantityAfter,
            BigDecimal positionAveragePrice,
            BigDecimal plannedStopLossPrice,
            String investmentReason,
            BigDecimal recent20dHigh,
            BigDecimal recent20dLow,
            BigDecimal recent5dReturnRate,
            BigDecimal executionRealizedProfit,
            OffsetDateTime quoteAt,
            OffsetDateTime executedAt
    ) {
        this.consumedEvent = consumedEvent;
        this.executionId = executionId;
        this.orderId = orderId;
        this.positionId = positionId;
        this.userId = userId;
        this.stockId = stockId;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
        this.tradeType = tradeType;
        this.newPosition = newPosition;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
        this.positionQuantityAfter = positionQuantityAfter;
        this.positionAveragePrice = positionAveragePrice;
        this.plannedStopLossPrice = plannedStopLossPrice;
        this.investmentReason = investmentReason;
        this.recent20dHigh = recent20dHigh;
        this.recent20dLow = recent20dLow;
        this.recent5dReturnRate = recent5dReturnRate;
        this.executionRealizedProfit = executionRealizedProfit;
        this.quoteAt = quoteAt;
        this.executedAt = executedAt;
    }
}
