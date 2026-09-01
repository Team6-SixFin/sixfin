package com.sparta.trading.domain.repository;

import com.sparta.trading.domain.entity.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StocksRepository extends JpaRepository<Stocks, Long> {

    Optional<Stocks> findBySymbol(String symbol);
}
