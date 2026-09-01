package com.sparta.trading.infrastructure.persistence.repository.candles;

import com.sparta.trading.domain.entity.DailyCandles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCandlesRepository extends JpaRepository<DailyCandles, Long> {
}
