package com.sparta.trading.infrastructure.persistence.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import com.sparta.trading.domain.repository.accounts.TradingAccountsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SearchResults;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TradingAccountsQueryRepositoryImpl implements TradingAccountsQueryRepository {

    private final TradingAccountsJpaRepository tradingAccountsJpaRepository;

    @Override
    public Page<Accounts> search(UUID userId, Pageable pageable) {
        return tradingAccountsJpaRepository.search(userId,pageable);
    }

    @Override
    public List<Accounts> findAllById(List<UUID> accountIds) {
        return tradingAccountsJpaRepository.findAllById(accountIds);
    }

    @Override
    public List<Accounts> findAllByUserId(UUID userId) {
        return tradingAccountsJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<Accounts> findByUserId(UUID userId) {
        return tradingAccountsJpaRepository.findByUserId(userId);
    }
}
