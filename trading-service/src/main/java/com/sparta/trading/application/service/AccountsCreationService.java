package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import com.sparta.trading.domain.repository.cashledgers.CashLedgersCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountsCreationService {

    private final AccountsCommandRepository accountRepository;
    private final CashLedgersCommandRepository cashLedgerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Accounts create(UUID userId) {
        Accounts account = accountRepository.saveAndFlush(Accounts.create(userId));
        cashLedgerRepository.save(CashLedgers.initialDeposit(account));
        return account;
    }
}
