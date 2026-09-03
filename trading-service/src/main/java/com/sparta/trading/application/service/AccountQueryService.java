package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.presentation.dto.response.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final AccountCreationService accountCreationService;

    public AccountResponse getOrCreateAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .map(AccountResponse::from)
                .orElseGet(() -> createOrGetAccount(userId));
    }

    private AccountResponse createOrGetAccount(UUID userId) {
        try {
            Accounts account = accountCreationService.create(userId);
            return AccountResponse.from(account);
        } catch (DataIntegrityViolationException exception) {
            return accountRepository.findByUserId(userId)
                    .map(AccountResponse::from)
                    .orElseThrow(() -> exception);
        }
    }
}
