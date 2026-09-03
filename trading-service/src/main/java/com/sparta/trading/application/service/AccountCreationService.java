package com.sparta.trading.application.service;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.entity.CashLedgers;
import com.sparta.trading.domain.repository.account.AccountRepository;
import com.sparta.trading.domain.repository.cashledger.CashLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountCreationService {

    private final AccountRepository accountRepository;
    private final CashLedgerRepository cashLedgerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Accounts create(UUID userId) {
        Accounts account = accountRepository.saveAndFlush(Accounts.create(userId));
        cashLedgerRepository.save(CashLedgers.initialDeposit(account));
        return account;
    }
}
