package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.AccountsCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountsCommandRepositoryImpl implements AccountsCommandRepository {

    private final AccountsJpaRepository accountJpaRepository;

    @Override
    public Optional<Accounts> findByUserIdForUpdate(UUID userId) {
        return accountJpaRepository.findByUserIdForUpdate(userId);
    }

    @Override
    public Accounts saveAndFlush(Accounts account) {
        return accountJpaRepository.saveAndFlush(account);
    }
}
