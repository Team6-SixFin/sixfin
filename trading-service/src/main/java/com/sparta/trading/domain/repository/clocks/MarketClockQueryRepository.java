package com.sparta.trading.domain.repository.clocks;

import com.sparta.trading.domain.entity.MarketClock;

import java.util.Optional;

public interface MarketClockQueryRepository {

    Optional<MarketClock> findById(int id);
}
