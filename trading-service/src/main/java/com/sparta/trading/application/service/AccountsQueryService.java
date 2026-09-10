package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.AccountsQueryRepository;
import com.sparta.trading.presentation.dto.response.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountsQueryService {

    private final AccountsQueryRepository accountsQueryRepository;
    private final AccountsCreationService accountCreationService;

    public AccountResponse getOrCreateAccount(UUID userId) {
        return accountsQueryRepository.findByUserId(userId)
                .map(AccountResponse::from)
                .orElseGet(() -> createOrGetAccount(userId));
    }

    private AccountResponse createOrGetAccount(UUID userId) {
        try {
            Accounts account = accountCreationService.create(userId);
            return AccountResponse.from(account);
        } catch (DataIntegrityViolationException exception) {
            return accountsQueryRepository.findByUserId(userId)
                    .map(AccountResponse::from)
                    .orElseThrow(() -> exception);
        }
    }
}
