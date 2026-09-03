package com.sparta.trading.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_accounts", schema = "trading_service")
public class Accounts {

    private static final BigDecimal DEFAULT_INITIAL_DEPOSIT = new BigDecimal("100000.0000");
    private static final String DEFAULT_CURRENCY = "USD";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "initial_deposit", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialDeposit;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Builder
    private Accounts(UUID userId, BigDecimal cashBalance, BigDecimal initialDeposit, String currency,
                     UUID createdBy, UUID updatedBy) {
        this.userId = userId;
        this.cashBalance = cashBalance;
        this.initialDeposit = initialDeposit;
        this.currency = currency;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public static Accounts create(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        return Accounts.builder()
                .userId(userId)
                .cashBalance(DEFAULT_INITIAL_DEPOSIT)
                .initialDeposit(DEFAULT_INITIAL_DEPOSIT)
                .currency(DEFAULT_CURRENCY)
                .createdBy(userId)
                .build();
    }

}
