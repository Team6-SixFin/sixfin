package com.sparta.trading.infrastructure.persistence.repository.positions;

import com.sparta.trading.domain.entity.PositionStatus;
import com.sparta.trading.domain.entity.Positions;
import com.sparta.trading.domain.repository.positions.DuplicateOpenPositionsGroup;
import com.sparta.trading.domain.repository.positions.PositionsCommandRepository;
import com.sparta.trading.domain.repository.positions.PositionsQuantityMismatchGroup;
import com.sparta.trading.domain.repository.positions.PositionsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PositionsCommandRepositoryImpl implements PositionsCommandRepository {

    private final PositionsJpaRepository positionJpaRepository;

    @Override
    public Optional<Positions> findOpenByAccountIdAndStockIdForUpdate(UUID accountId, Long stockId) {
        return positionJpaRepository.findOpenByAccountIdAndStockIdForUpdate(accountId, stockId);
    }

    @Override
    public Positions save(Positions position) {
        return positionJpaRepository.save(position);
    }

}
