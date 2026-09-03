package com.sparta.trading.domain.repository.account;

import com.sparta.trading.domain.entity.Accounts;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Accounts> findByUserId(UUID userId);

    Accounts saveAndFlush(Accounts account);
}
