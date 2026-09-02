package com.sparta.trading.infrastructure.persistence.repository.stocks;

import com.sparta.trading.domain.entity.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StocksRepository extends JpaRepository<Stocks, Long> {

    Optional<Stocks> findBySymbol(String symbol);

    @Query("SELECT s.id FROM Stocks s WHERE s.symbol IN :symbolList")
    List<Long> findIdBySymbolIn(List<String> symbolList);
}
