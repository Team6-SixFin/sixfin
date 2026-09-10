package com.sparta.trading.infrastructure.persistence.repository.clocks;

import com.sparta.trading.domain.entity.MarketClock;
import com.sparta.trading.domain.repository.clocks.MarketClockCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketClockCommandRepositoryImpl implements MarketClockCommandRepository {

    private final MarketClockJpaRepository marketClockJpaRepository;

    @Override
    public Optional<MarketClock> findForUpdate() {
        return marketClockJpaRepository.findForUpdate();
    }

    @Override
    public boolean existsById(int id) {
        return marketClockJpaRepository.existsById(id);
    }

    @Override
    public MarketClock save(MarketClock marketClock) {
        return marketClockJpaRepository.save(marketClock);
    }
}
