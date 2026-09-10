package com.sparta.trading.domain.repository.clocks;

import com.sparta.trading.domain.entity.MarketClock;

import java.util.Optional;

public interface MarketClockCommandRepository {

    Optional<MarketClock> findForUpdate();

    boolean existsById(int id);

    MarketClock save(MarketClock marketClock);
}
