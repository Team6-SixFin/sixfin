package com.sparta.trading.infrastructure.persistence.repository.position;

import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.position.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

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
      
    public List<Positions> findAllByAccountIdAndStatus(UUID id, String status) {
        return positionJpaRepository.findAllByAccountIdAndStatus(id,status);
    }
}
