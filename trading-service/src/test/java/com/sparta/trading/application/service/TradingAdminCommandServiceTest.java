package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import com.sparta.trading.domain.repository.positions.PositionsQueryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.infrastructure.messaging.kafka.service.OutboxPublishResult;
import com.sparta.trading.infrastructure.messaging.kafka.service.TradingKafkaOutboxMarker;
import com.sparta.trading.infrastructure.messaging.kafka.service.TradingKafkaOutboxPublisher;
import com.sparta.trading.presentation.dto.request.TradingAdminResetAccountRequest;
import com.sparta.trading.presentation.dto.request.TradingAdminRetryOutBoxRequest;
import com.sparta.trading.presentation.dto.response.TradingAdminResetAccountResponse;
import com.sparta.trading.presentation.dto.response.TradingAdminRetryOutBoxResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAdminCommandServiceTest {

    @Mock
    private AccountsQueryRepository tradingAccountsQueryRepository;

    @Mock
    private PositionsQueryRepository positionRepository;

    @Mock
    private CashLedgersCommandRepository cashLedgerRepository;

    @Mock
    private TradingKafkaOutboxMarker outboxMarker;

    @Mock
    private TradingKafkaOutboxPublisher outboxPublisher;

    private TradingAdminCommandService service;
    private UUID targetUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        service = new TradingAdminCommandService(
                tradingAccountsQueryRepository, positionRepository, cashLedgerRepository,
                outboxMarker, outboxPublisher
        );
        targetUserId = UUID.randomUUID();
        adminUserId = UUID.randomUUID();
        lenient().when(cashLedgerRepository.save(any())).thenAnswer(invocation -> {
            CashLedgers ledger = invocation.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 500L);
            return ledger;
        });
    }

    @Test
    @DisplayName("예수금 차이와 OPEN 포지션이 있으면 상계 원장을 남기고 초기화한다")
    void resetAccounts_closesOpenPositionsAndRecordsAdjustmentLedger() {
        Accounts account = accountOf(targetUserId);
        account.withdraw(new BigDecimal("6111.7000"));
        when(tradingAccountsQueryRepository.findByUserId(targetUserId)).thenReturn(Optional.of(account));
        List<Positions> openPositions = List.of(
                openPosition(account.getId(), targetUserId),
                openPosition(account.getId(), targetUserId)
        );
        when(positionRepository.findAllOpenByAccountId(account.getId())).thenReturn(openPositions);

        TradingAdminResetAccountResponse response = service.resetAccounts(
                targetUserId, adminUserId,
                new TradingAdminResetAccountRequest("MVP 시연 리허설 초기화", null)
        );

        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.userId()).isEqualTo(targetUserId);
        assertThat(response.cashBalanceBefore()).isEqualByComparingTo("93888.3000");
        assertThat(response.cashBalanceAfter()).isEqualByComparingTo("100000.0000");
        assertThat(response.adjustmentAmount()).isEqualByComparingTo("6111.7000");
        assertThat(response.closedPositionCount()).isEqualTo(2);
        assertThat(response.ledgerId()).isEqualTo(500L);
        assertThat(response.resetAt()).isNotNull();
        assertThat(account.getCashBalance()).isEqualByComparingTo("100000.0000");
        assertThat(account.getInitialDeposit()).isEqualByComparingTo("100000.0000");

        for (Positions position : openPositions) {
            assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED.name());
            assertThat(position.getClosedAt()).isEqualTo(response.resetAt());
            assertThat(position.getUpdatedBy()).isEqualTo(adminUserId);
        }
        verify(cashLedgerRepository).save(any(CashLedgers.class));
    }

    @Test
    @DisplayName("initialDeposit을 지정하면 그 값을 기준으로 재설정한다")
    void resetAccounts_usesRequestedInitialDepositWhenProvided() {
        Accounts account = accountOf(targetUserId);
        when(tradingAccountsQueryRepository.findByUserId(targetUserId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(account.getId())).thenReturn(List.of());

        TradingAdminResetAccountResponse response = service.resetAccounts(
                targetUserId, adminUserId,
                new TradingAdminResetAccountRequest("초기 지급액 변경", new BigDecimal("50000"))
        );

        assertThat(response.cashBalanceBefore()).isEqualByComparingTo("100000.0000");
        assertThat(response.cashBalanceAfter()).isEqualByComparingTo("50000");
        assertThat(response.adjustmentAmount()).isEqualByComparingTo("-50000");
        assertThat(response.closedPositionCount()).isEqualTo(0);
        assertThat(account.getInitialDeposit()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("예수금이 이미 초기값이고 OPEN 포지션도 없으면 409로 거부한다")
    void resetAccounts_rejectsWhenNothingToReset() {
        Accounts account = accountOf(targetUserId);
        when(tradingAccountsQueryRepository.findByUserId(targetUserId)).thenReturn(Optional.of(account));
        when(positionRepository.findAllOpenByAccountId(account.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.resetAccounts(
                targetUserId, adminUserId, new TradingAdminResetAccountRequest("사유", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.NOTHING_TO_RESET));

        verify(cashLedgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("대상 사용자의 계좌가 없으면 404로 거부한다")
    void resetAccounts_throwsWhenAccountNotFound() {
        when(tradingAccountsQueryRepository.findByUserId(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetAccounts(
                targetUserId, adminUserId, new TradingAdminResetAccountRequest("사유", null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(TradingErrorCode.ACCOUNT_NOT_FOUND));

        verify(positionRepository, never()).findAllOpenByAccountId(any());
    }

    @Test
    @DisplayName("payload 없이 재발행하면 payload를 덮어쓰지 않고 publishOne만 호출한다")
    void retryOutBoxWithoutPayloadOverwrite() {
        Long outboxId = 10L;
        when(outboxPublisher.publishOne(outboxId)).thenReturn(OutboxPublishResult.PUBLISHED);

        TradingAdminRetryOutBoxResponse response = service.retryOutBoxResponse(
                outboxId, new TradingAdminRetryOutBoxRequest(null));

        assertThat(response.result()).isEqualTo(OutboxPublishResult.PUBLISHED);
        assertThat(response.payloadOverwritten()).isFalse();
        verify(outboxMarker, never()).overwritePayload(anyLong(), any());
        verify(outboxPublisher).publishOne(outboxId);
    }

    @Test
    @DisplayName("payload가 있으면 먼저 덮어쓴 뒤 publishOne을 호출한다")
    void retryOutBoxWithPayloadOverwrite() {
        Long outboxId = 11L;
        Map<String, Object> payload = Map.of("fixed", true);
        JsonNode expectedPayload = new ObjectMapper().valueToTree(payload);
        when(outboxPublisher.publishOne(outboxId)).thenReturn(OutboxPublishResult.FAILED);

        TradingAdminRetryOutBoxResponse response = service.retryOutBoxResponse(
                outboxId, new TradingAdminRetryOutBoxRequest(payload));

        assertThat(response.result()).isEqualTo(OutboxPublishResult.FAILED);
        assertThat(response.payloadOverwritten()).isTrue();
        verify(outboxMarker).overwritePayload(outboxId, expectedPayload);
        verify(outboxPublisher).publishOne(outboxId);
    }

    private Accounts accountOf(UUID userId) {
        Accounts account = Accounts.create(userId);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    private Positions openPosition(UUID accountId, UUID userId) {
        return Positions.open(accountId, 1L, 10, new BigDecimal("100.0000"), null,
                "장기 성장 기대", Instant.parse("2026-09-01T00:00:00Z"), 12L, userId);
    }
}
