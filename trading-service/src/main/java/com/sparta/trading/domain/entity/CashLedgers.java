package com.sparta.trading.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static jakarta.persistence.FetchType.LAZY;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_cash_ledgers",
        schema = "trading_service",
        indexes = @Index(name = "idx_cash_ledgers_account_id", columnList = "account_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_cash_ledgers_execution_id", columnNames = "execution_id")
)
public class CashLedgers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cash_ledgers_account_id")
    )
    private Accounts account;

    @Column(name = "execution_id")
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private CashLedgerTxType txType;

    @Column(name = "amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 20, scale = 4)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private CashLedgers(Accounts account, UUID executionId, CashLedgerTxType txType,
                        BigDecimal amount, BigDecimal balanceAfter) {
        this.account = Objects.requireNonNull(account, "account must not be null");
        this.executionId = executionId;
        this.txType = Objects.requireNonNull(txType, "txType must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
    }

    public static CashLedgers initialDeposit(Accounts account) {
        Objects.requireNonNull(account, "account must not be null");

        return CashLedgers.builder()
                .account(account)
                .txType(CashLedgerTxType.INITIAL_DEPOSIT)
                .amount(account.getInitialDeposit())
                .balanceAfter(account.getCashBalance())
                .build();
    }
}
