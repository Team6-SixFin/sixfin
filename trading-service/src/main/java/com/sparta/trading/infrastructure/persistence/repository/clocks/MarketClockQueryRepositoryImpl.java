package com.sparta.trading.infrastructure.persistence.repository.clocks;

import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.repository.clocks.MarketClockQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketClockQueryRepositoryImpl implements MarketClockQueryRepository {

    private final MarketClockJpaRepository marketClockJpaRepository;

    @Override
    public Optional<MarketClock> findById(int id) {
        return marketClockJpaRepository.findById(id);
    }
}
