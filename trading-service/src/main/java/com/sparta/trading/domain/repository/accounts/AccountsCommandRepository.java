package com.sparta.trading.domain.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;

import java.util.Optional;
import java.util.UUID;

public interface AccountsCommandRepository {

    Optional<Accounts> findByUserIdForUpdate(UUID userId);

    Accounts saveAndFlush(Accounts account);
}
