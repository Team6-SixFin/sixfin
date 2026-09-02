package com.sparta.trading.domain.entity;

import com.sparta.trading.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CollectionId;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_orders" )
public class Orders extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id")
    private UUID id;


    @Column(name="request_id")
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


    @Column(name = "reject_Reason",  length = 100)
    private String rejectReason;

    @Column(name = "planned_stop_loss_price", precision = 19, scale = 4)
    private BigDecimal plannedStopLossPrice;

    @Column(name = "investment_reason",  length = 500)
    private String investmentReason;

    @Column(name = "market_time", nullable = false)
    private Instant marketTime;

    @Column(name = "candle_seq", nullable = false)
    private Long candleSeq;
}
