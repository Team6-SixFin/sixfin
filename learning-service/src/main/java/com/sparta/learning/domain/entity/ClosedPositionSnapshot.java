package com.sparta.learning.domain.entity;

import com.sparta.learning.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "closed_position_snapshots",
        indexes = {
                @Index(name = "idx_closed_position_user_id", columnList = "user_id"),
                @Index(name = "idx_closed_position_stock_id", columnList = "stock_id"),
                @Index(name = "idx_closed_position_closed_at", columnList = "closed_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClosedPositionSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consumed_event_id", nullable = false, unique = true)
    private ConsumedEvent consumedEvent;

    @Column(name = "position_id", nullable = false, unique = true)
    private UUID positionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "stock_symbol", nullable = false, length = 20)
    private String stockSymbol;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "total_bought_quantity", nullable = false)
    private long totalBoughtQuantity;

    @Column(name = "total_sold_quantity", nullable = false)
    private long totalSoldQuantity;

    @Column(name = "average_entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageEntryPrice;

    @Column(name = "average_exit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageExitPrice;

    @Column(name = "planned_stop_loss_price", precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    @Column(name = "realized_profit", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedProfit;

    @Column(name = "realized_return_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal realizedReturnRate;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at", nullable = false)
    private OffsetDateTime closedAt;

    @Builder
    private ClosedPositionSnapshot(
            ConsumedEvent consumedEvent,
            UUID positionId,
            UUID userId,
            Long stockId,
            String stockSymbol,
            String stockName,
            long totalBoughtQuantity,
            long totalSoldQuantity,
            BigDecimal averageEntryPrice,
            BigDecimal averageExitPrice,
            BigDecimal plannedStopLossPrice,
            BigDecimal realizedProfit,
            BigDecimal realizedReturnRate,
            OffsetDateTime openedAt,
            OffsetDateTime closedAt
    ) {
        this.consumedEvent = consumedEvent;
        this.positionId = positionId;
        this.userId = userId;
        this.stockId = stockId;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
        this.totalBoughtQuantity = totalBoughtQuantity;
        this.totalSoldQuantity = totalSoldQuantity;
        this.averageEntryPrice = averageEntryPrice;
        this.averageExitPrice = averageExitPrice;
        this.plannedStopLossPrice = plannedStopLossPrice;
        this.realizedProfit = realizedProfit;
        this.realizedReturnRate = realizedReturnRate;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }
}
