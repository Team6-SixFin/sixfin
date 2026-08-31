package com.sparta.trading.domain.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TradingAccountsQueryRepository {
    Page<Accounts> search(UUID uuid, Pageable pageable);
}
