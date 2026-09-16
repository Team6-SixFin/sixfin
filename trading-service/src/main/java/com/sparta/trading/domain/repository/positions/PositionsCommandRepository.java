package com.sparta.trading.domain.repository.positions;

import com.sparta.trading.domain.entity.Positions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionsCommandRepository {

    Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId);

    List<Positions> findAllOpenByAccountIdForUpdate(UUID accountId);

    Positions save(Positions position);
}
