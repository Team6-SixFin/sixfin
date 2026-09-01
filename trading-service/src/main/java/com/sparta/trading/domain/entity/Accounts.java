package com.sparta.trading.domain.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_accounts", schema = "trading_service")
public class Accounts {

    @Id
    @Column(name ="id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "initial_deposit", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialDeposit;

    @Column(name ="currency", nullable = false,length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name="created_At",updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name="updated_At")
    private Instant updatedAt;


}
