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
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;

import com.sparta.trading.global.entity.AuditableEntity;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_accounts", schema = "trading_service")
public class Accounts extends AuditableEntity {

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

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Builder
    private Accounts(UUID userId, BigDecimal cashBalance, BigDecimal initialDeposit, String currency) {
        this.userId = userId;
        this.cashBalance = cashBalance;
        this.initialDeposit = initialDeposit;
        this.currency = currency;
    }

    public static Accounts create(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Accounts account = Accounts.builder()
                .userId(userId)
                .cashBalance(DEFAULT_INITIAL_DEPOSIT)
                .initialDeposit(DEFAULT_INITIAL_DEPOSIT)
                .currency(DEFAULT_CURRENCY)
                .build();
        account.initializeAudit(userId);
        return account;
    }

    /** 계좌 행을 잠근 주문 트랜잭션에서만 호출한다. */
    public BigDecimal withdraw(BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        if (normalizedAmount.signum() <= 0 || cashBalance.compareTo(normalizedAmount) < 0) {
            throw new IllegalArgumentException("insufficient cash balance");
        }

        cashBalance = cashBalance.subtract(normalizedAmount).setScale(4, RoundingMode.HALF_UP);
        return cashBalance;
    }

}
