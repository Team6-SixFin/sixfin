package com.sparta.trading.infrastructure.persistence.repository.stocks;

import com.sparta.trading.domain.entity.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StocksRepository extends JpaRepository<Stocks, Long> {

    Optional<Stocks> findBySymbol(String symbol);

    List<Long> findStockIdBySymbolIn(List<String> symbolList);
}
