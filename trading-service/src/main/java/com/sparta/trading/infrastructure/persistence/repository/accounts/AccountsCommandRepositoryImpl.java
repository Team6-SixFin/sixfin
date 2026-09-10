package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.AccountsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountsRepository {

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
