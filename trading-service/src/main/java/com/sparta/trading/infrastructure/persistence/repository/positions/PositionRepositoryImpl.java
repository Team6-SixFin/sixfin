package com.sparta.trading.infrastructure.persistence.repository.positions;

import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.positions.DuplicateOpenPositionsGroup;
import com.sparta.trading.domain.repository.positions.PositionsQuantityMismatchGroup;
import com.sparta.trading.domain.repository.positions.PositionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionsRepository {

    private final PositionJpaRepository positionJpaRepository;

    @Override
    public Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId) {
        return positionJpaRepository.findOpenByAccountIdAndStockIdForUpdate(accountId, stockId);
    }

    @Override
    public Positions save(Positions position) {
        return positionJpaRepository.save(position);
    }

    @Override
    public List<Positions> findAllOpenByAccountId(UUID accountId) {
        return positionJpaRepository.findAllOpenByAccountId(accountId);
    }

    @Override
    public Page<Positions> findAllByUserIdAndStatus(
            UUID userId,
            PositionStatus status,
            Pageable pageable
    ) {
        return positionJpaRepository.findAllByUserIdAndStatus(
                userId,
                status.name(),
                pageable
        );
    }

    @Override
    public Optional<Positions> findByIdAndUserId(UUID positionId, UUID userId) {
        return positionJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(
                positionId,
                userId
        );
    }

    @Override
    public List<Positions> findAllByAccountIdAndStatus(UUID id, String status) {
        return positionJpaRepository.findAllByAccountIdAndStatus(id, status);
    }

    @Override
    public long countNegativeQuantityByAccountId(UUID accountId) {
        return positionJpaRepository.countNegativeQuantityByAccountId(accountId);
    }

    @Override
    public List<Positions> findNegativeQuantityByAccountId(UUID accountId, Pageable pageable) {
        return positionJpaRepository.findNegativeQuantityByAccountId(accountId, pageable);
    }

    @Override
    public List<DuplicateOpenPositionsGroup> findDuplicateOpenPositionGroups(UUID accountId) {
        return positionJpaRepository.findDuplicateOpenPositionGroups(accountId);
    }

    @Override
    public List<PositionsQuantityMismatchGroup> findPositionQuantityMismatches(UUID accountId) {
        return positionJpaRepository.findPositionQuantityMismatches(accountId);
    }
}
