package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.presentation.dto.response.AccountResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountCreationService accountCreationService;

    @InjectMocks
    private AccountQueryService accountQueryService;

    @Test
    void getOrCreateAccount_returnsExistingAccount() {
        UUID userId = UUID.randomUUID();
        Accounts account = Accounts.create(userId);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));

        AccountResponse response = accountQueryService.getOrCreateAccount(userId);

        assertThat(response.cashBalance()).isEqualByComparingTo("100000.0000");
        assertThat(response.currency()).isEqualTo("USD");
        verifyNoInteractions(accountCreationService);
    }

    @Test
    void getOrCreateAccount_createsAccountWhenMissing() {
        UUID userId = UUID.randomUUID();
        Accounts account = Accounts.create(userId);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountCreationService.create(userId)).thenReturn(account);

        AccountResponse response = accountQueryService.getOrCreateAccount(userId);

        assertThat(response.initialDeposit()).isEqualByComparingTo("100000.0000");
        verify(accountCreationService).create(userId);
    }

    @Test
    void getOrCreateAccount_reloadsAccountAfterConcurrentCreation() {
        UUID userId = UUID.randomUUID();
        Accounts account = Accounts.create(userId);
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.<Accounts>empty())
                .thenReturn(Optional.of(account));
        when(accountCreationService.create(userId))
                .thenThrow(new DataIntegrityViolationException("duplicate user_id"));

        AccountResponse response = accountQueryService.getOrCreateAccount(userId);

        assertThat(response.cashBalance()).isEqualByComparingTo("100000.0000");
        verify(accountRepository, times(2)).findByUserId(userId);
    }
}
