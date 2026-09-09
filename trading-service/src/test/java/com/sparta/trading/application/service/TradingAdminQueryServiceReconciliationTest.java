package com.sparta.trading.application.service;

import com.sparta.trading.application.dto.query.TradingReconciliationQuery;
import com.sparta.trading.domain.entity.ReconciliationStatus;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import com.sparta.trading.domain.repository.execution.TradingExecutionQueryRepository;
import com.sparta.trading.domain.repository.order.OrderRepository;
import com.sparta.trading.domain.repository.order.TradingOrderQueryRepository;
import com.sparta.trading.domain.repository.outboxEvent.TradingOutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.position.PositionRepository;
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

    @Mock private TradingAccountsQueryRepository tradingAccountsQueryRepository;
    @Mock private TradingOrderQueryRepository tradingOrderQueryRepository;
    @Mock private TradingExecutionQueryRepository tradingExecutionQueryRepository;
    @Mock private TradingOutboxEventsQueryRepository tradingOutboxEventsQueryRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private StocksRepository stocksRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CashLedgerRepository cashLedgerRepository;
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
