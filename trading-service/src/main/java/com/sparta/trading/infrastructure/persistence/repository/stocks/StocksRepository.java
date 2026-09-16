package com.sparta.trading.infrastructure.persistence.repository.stocks;

import com.sparta.trading.domain.entity.Stocks;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StocksRepository extends JpaRepository<Stocks, Long> {

    Optional<Stocks> findBySymbol(String symbol);

    @Query("SELECT s.id FROM Stocks s WHERE s.symbol IN :symbolList AND s.active = true")
    List<Long> findIdBySymbolIn(List<String> symbolList);

    @Query("SELECT s.symbol DISTINCT FROM Stocks s WHERE s.active = true")
    Set<String> findAllSymbol();
}
