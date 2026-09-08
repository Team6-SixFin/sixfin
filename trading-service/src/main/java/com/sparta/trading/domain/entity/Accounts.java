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

    /** 예수금 차감: 예수금에서 지정한 금액을 차감하고 잔액 반환 */
    // 계좌 행을 잠근 주문 트랜잭션에서만 호출한다
    public BigDecimal withdraw(BigDecimal amount) {
        // 차감할 금액을 소수점 4자리로 맞추고 반올림
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_UP);

        // 차감 금액이 0 이하거나 예수금보다 차감 금액이 더 크면 예외 처리
        if (normalizedAmount.signum() <= 0 || cashBalance.compareTo(normalizedAmount) < 0) {
            throw new IllegalArgumentException("insufficient cash balance");
        }

        // 예수금에서 차감한 후 소수점 4자리 맞추고 반올림
        cashBalance = cashBalance.subtract(normalizedAmount).setScale(4, RoundingMode.HALF_UP);
        return cashBalance;
    }

    /** 예수금 증가: 예수금에 지정한 금액을 추가해 증가 시킨후 잔액 반환 */
    // 계좌 행을 잠근 주문 트랜잭션에서만 호출한다
    public BigDecimal deposit(BigDecimal amount) {
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        if (normalizedAmount.signum() <= 0) {
            throw new IllegalArgumentException("deposit amount must be positive");
        }

        cashBalance = cashBalance.add(normalizedAmount).setScale(4, RoundingMode.HALF_UP);
        return cashBalance;
    }

}
