package com.sparta.trading.domain.entity;

import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_executions")
public class Executions extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id")
    private UUID id;

    @Column(name="order_Id" ,nullable = false)
    private UUID orderId;

    @Column(name="position_id")
    private UUID positionId;

    @Column(name="user_id" ,nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "side", nullable = false, length = 10)
    private String side;

    @Column(name = "executed_price", precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(name="executed_quantity", nullable = false)
    private Integer executedQuantity;

    @Column(name = "executed_amount", precision = 19, scale = 4)
    private BigDecimal executedAmount;

    @Column(name = "avg_entry_price_at_execution", precision = 19, scale = 4)
    private BigDecimal avgEntyPriceAtExecuation;

    @Column(name = "realized_profit", precision = 19, scale = 4)
    private BigDecimal realizedProfit;

    @Column(name = "market_time", nullable = false)
    private Instant marketTime;

    @Column(name = "candle_seq", nullable = false)
    private Long candleSeq;
}
