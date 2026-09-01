package com.sparta.trading.domain.repository;

import com.sparta.trading.domain.entity.DailyCandles;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCandlesRepository extends JpaRepository<DailyCandles, Long> {
}
