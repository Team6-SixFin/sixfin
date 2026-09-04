package com.sparta.trading.infrastructure.persistence.repository.account;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Optional<Accounts> findByUserId(UUID userId) {
        return accountJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<Accounts> findByUserIdForUpdate(UUID userId) {
        return accountJpaRepository.findByUserIdForUpdate(userId);
    }

    @Override
    public Accounts saveAndFlush(Accounts account) {
        return accountJpaRepository.saveAndFlush(account);
    }
}
