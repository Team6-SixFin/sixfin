package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import com.sparta.trading.global.exception.CustomException;
import com.sparta.trading.global.exception.TradingErrorCode;
import com.sparta.trading.global.response.PageResponse;
import com.sparta.trading.presentation.dto.response.CashLedgerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashLedgerQueryServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CashLedgerRepository cashLedgerRepository;

    @InjectMocks
    private CashLedgerQueryService cashLedgerQueryService;

    @Test
    void getCashLedgers_returnsFilteredLedgerAndNormalizesInvalidPageSize() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Accounts account = accountOf(accountId);
        CashLedgers cashLedger = cashLedgerOf(executionId, CashLedgerTxType.BUY);
        PageRequest requestedPageable = PageRequest.of(1, 25);
        PageRequest normalizedPageable = PageRequest.of(1, 20);
        Page<CashLedgers> cashLedgerPage = new PageImpl<>(
                List.of(cashLedger),
                normalizedPageable,
                21
        );

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(cashLedgerRepository.findAllByAccountIdAndTxType(
                accountId,
                CashLedgerTxType.BUY,
                normalizedPageable
        )).thenReturn(cashLedgerPage);

        PageResponse<CashLedgerResponse> response = cashLedgerQueryService.getCashLedgers(
                userId,
                CashLedgerTxType.BUY,
                requestedPageable
        );

        assertThat(response.getContent()).singleElement().satisfies(cashLedgerResponse -> {
            assertThat(cashLedgerResponse.ledgerId()).isEqualTo(42L);
            assertThat(cashLedgerResponse.executionId()).isEqualTo(executionId);
            assertThat(cashLedgerResponse.txType()).isEqualTo(CashLedgerTxType.BUY);
            assertThat(cashLedgerResponse.amount()).isEqualByComparingTo("-2000.0000");
            assertThat(cashLedgerResponse.balanceAfter()).isEqualByComparingTo("98000.0000");
        });
        assertThat(response.getPageInfo().getPaginationType()).isEqualTo("OFFSET");
        assertThat(response.getPageInfo().getPage()).isEqualTo(1);
        assertThat(response.getPageInfo().getSize()).isEqualTo(20);
        assertThat(response.getPageInfo().getTotalElements()).isEqualTo(21);
        assertThat(response.getPageInfo().getTotalPages()).isEqualTo(2);
        assertThat(response.getSummary()).isNull();

        verify(cashLedgerRepository).findAllByAccountIdAndTxType(
                accountId,
                CashLedgerTxType.BUY,
                normalizedPageable
        );
    }

    @Test
    void getCashLedgers_returnsAllLedgerWhenTxTypeIsNull() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Accounts account = accountOf(accountId);
        PageRequest pageable = PageRequest.of(0, 30);
        Page<CashLedgers> cashLedgerPage = new PageImpl<>(List.of(), pageable, 0);

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(cashLedgerRepository.findAllByAccountIdAndTxType(accountId, null, pageable))
                .thenReturn(cashLedgerPage);

        PageResponse<CashLedgerResponse> response = cashLedgerQueryService.getCashLedgers(
                userId,
                null,
                pageable
        );

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPageInfo().getSize()).isEqualTo(30);
        assertThat(response.getSummary()).isNull();
        verify(cashLedgerRepository).findAllByAccountIdAndTxType(
                accountId,
                null,
                pageable
        );
    }

    @Test
    void getCashLedgers_throwsWhenAccountDoesNotExist() {
        UUID userId = UUID.randomUUID();

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashLedgerQueryService.getCashLedgers(
                userId,
                null,
                PageRequest.of(0, 20)
        )).isInstanceOfSatisfying(CustomException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(TradingErrorCode.ACCOUNT_NOT_FOUND));

        verifyNoInteractions(cashLedgerRepository);
    }

    private Accounts accountOf(UUID accountId) {
        Accounts account = mock(Accounts.class);
        when(account.getId()).thenReturn(accountId);
        return account;
    }

    private CashLedgers cashLedgerOf(UUID executionId, CashLedgerTxType txType) {
        CashLedgers cashLedger = mock(CashLedgers.class);
        when(cashLedger.getId()).thenReturn(42L);
        when(cashLedger.getExecutionId()).thenReturn(executionId);
        when(cashLedger.getTxType()).thenReturn(txType);
        when(cashLedger.getAmount()).thenReturn(new BigDecimal("-2000.0000"));
        when(cashLedger.getBalanceAfter()).thenReturn(new BigDecimal("98000.0000"));
        when(cashLedger.getCreatedAt()).thenReturn(Instant.parse("2026-09-07T01:30:00Z"));
        return cashLedger;
    }
}
