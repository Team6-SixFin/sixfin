package com.sparta.trading.domain.repository;

import com.sparta.trading.domain.entity.PriceCandles;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceCandlesRepository extends JpaRepository<PriceCandles, Long> {

    Optional<PriceCandles> findFirstBySeq(Long seq);
}
