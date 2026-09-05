package com.sparta.trading.domain.repository.position;

import com.sparta.trading.domain.entity.Positions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository {

    Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId);

    Positions save(Positions position);

    // 현재 보유 중인 모든 포지션 조회
    List<Positions> findAllOpenByAccountId(UUID accountId);
}
