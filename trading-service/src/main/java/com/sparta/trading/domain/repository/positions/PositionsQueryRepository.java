package com.sparta.trading.domain.repository.positions;

import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionsQueryRepository {

    // 현재 보유 중인 모든 포지션 조회
    List<Positions> findAllOpenByAccountId(UUID accountId);

    Page<Positions> findAllByUserIdAndStatus(
            UUID userId,
            PositionStatus status,
            Pageable pageable
    );

    Optional<Positions> findByIdAndUserId(UUID positionId, UUID userId);
  
    List<Positions> findAllByAccountIdAndStatus(UUID id, String status);

    long countNegativeQuantityByAccountId(UUID accountId);

    List<Positions> findNegativeQuantityByAccountId(UUID accountId, Pageable pageable);

    List<DuplicateOpenPositionsGroup> findDuplicateOpenPositionGroups(UUID accountId);

    List<PositionsQuantityMismatchGroup> findPositionQuantityMismatches(UUID accountId);
}
