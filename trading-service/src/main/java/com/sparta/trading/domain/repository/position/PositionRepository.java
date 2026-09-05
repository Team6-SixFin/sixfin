package com.sparta.trading.domain.repository.position;

import com.sparta.trading.domain.entity.Positions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository {

    Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId);

    Positions save(Positions position);

    List<Positions> findAllByAccountIdAndStatus(UUID id, String status);
}
