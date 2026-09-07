package com.sparta.trading.domain.repository.accounts;

import com.sparta.trading.domain.entity.Accounts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradingAccountsQueryRepository {
    Page<Accounts> search(UUID uuid, Pageable pageable);

    //어드민 - 주문 목록 전체조회에서 accountId를 통해 userId를 찾는 쿼리
    List<Accounts> findAllById(List<UUID> accountIds);

    List<Accounts> findAllByUserId(UUID uuid);

    Optional<Accounts> findByUserId (UUID userId);
}
