package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgerTxType;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountCreationServiceTest {

    @Mock
    private AccountsCommandRepository accountRepository;

    @Mock
    private CashLedgersCommandRepository cashLedgerRepository;

    @InjectMocks
    private AccountsCreationService accountCreationService;

    @Test
    void create_savesAccountAndInitialDepositLedger() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.saveAndFlush(any(Accounts.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Accounts.class));

        Accounts account = accountCreationService.create(userId);

        ArgumentCaptor<CashLedgers> ledgerCaptor = ArgumentCaptor.forClass(CashLedgers.class);
        verify(cashLedgerRepository).save(ledgerCaptor.capture());
        CashLedgers ledger = ledgerCaptor.getValue();

        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getCashBalance()).isEqualByComparingTo("100000.0000");
        assertThat(ledger.getAccount()).isSameAs(account);
        assertThat(ledger.getTxType()).isEqualTo(CashLedgerTxType.INITIAL_DEPOSIT);
        assertThat(ledger.getAmount()).isEqualByComparingTo("100000.0000");
        assertThat(ledger.getBalanceAfter()).isEqualByComparingTo("100000.0000");
    }
}
