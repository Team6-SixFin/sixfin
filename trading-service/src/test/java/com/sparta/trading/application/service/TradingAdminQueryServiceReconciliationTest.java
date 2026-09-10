package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingReconciliationQuery;
import com.sparta.trading.domain.entity.ReconciliationStatus;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersQueryRepository;
import com.sparta.trading.domain.repository.executions.ExecutionsQueryRepository;
import com.sparta.trading.domain.repository.orders.OrdersRepository;
import com.sparta.trading.domain.repository.orders.OrdersQueryRepository;
import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.positions.PositionsRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.persistence.repository.stocks.StocksRepository;
import com.sparta.trading.presentation.dto.response.TradingReconciliationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAdminQueryServiceReconciliationTest {

    @Mock private AccountsQueryRepository tradingAccountsQueryRepository;
    @Mock private OrdersQueryRepository tradingOrderQueryRepository;
    @Mock private ExecutionsQueryRepository tradingExecutionQueryRepository;
    @Mock private OutboxEventsQueryRepository tradingOutboxEventsQueryRepository;
    @Mock private PositionsRepository positionRepository;
    @Mock private StocksRepository stocksRepository;
    @Mock private OrdersRepository orderRepository;
    @Mock private CashLedgersQueryRepository cashLedgerRepository;
    @Mock private StringRedisTemplate redisTemplate;

    private TradingAdminQueryService service() {
        return new TradingAdminQueryService(
                tradingAccountsQueryRepository, tradingOrderQueryRepository, tradingExecutionQueryRepository,
                tradingOutboxEventsQueryRepository, positionRepository, stocksRepository, orderRepository,
                cashLedgerRepository, redisTemplate
        );
    }

    @Test
    void reconciliation_returnsOkWhenNoMismatch() {
        when(tradingAccountsQueryRepository.countNegativeCashBalance(null)).thenReturn(0L);

        TradingReconciliationResponse response = service().reconciliation(
                new TradingReconciliationQuery(null, "NEGATIVE_CASH", false));

        assertThat(response.overallStatus()).isEqualTo(ReconciliationStatus.OK);
        assertThat(response.scope()).isEqualTo("ALL");
        assertThat(response.checks()).hasSize(1);
        assertThat(response.checks().get(0).mismatchedCount()).isZero();
    }

    @Test
    void reconciliation_returnsMismatchWhenCountIsPositive() {
        when(tradingAccountsQueryRepository.countNegativeCashBalance(null)).thenReturn(2L);

        TradingReconciliationResponse response = service().reconciliation(
                new TradingReconciliationQuery(null, "NEGATIVE_CASH", false));

        assertThat(response.overallStatus()).isEqualTo(ReconciliationStatus.MISMATCH);
        assertThat(response.checks().get(0).mismatchedCount()).isEqualTo(2);
    }

    @Test
    void reconciliation_rejectsUnknownCheckCode() {
        assertThatThrownBy(() -> service().reconciliation(
                new TradingReconciliationQuery(null, "NOT_A_REAL_CODE", false)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.INVALID_CHECK_CODE));
    }

    @Test
    void reconciliation_rejectsUnknownAccountId() {
        UUID accountId = UUID.randomUUID();
        when(tradingAccountsQueryRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reconciliation(
                new TradingReconciliationQuery(accountId, "NEGATIVE_CASH", false)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.ACCOUNT_NOT_FOUND));
    }
}
