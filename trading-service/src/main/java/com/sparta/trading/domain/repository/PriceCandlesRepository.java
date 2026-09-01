package com.sparta.trading.domain.repository;

import com.sparta.trading.domain.entity.PriceCandles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PriceCandlesRepository extends JpaRepository<PriceCandles, Long> {

    Optional<PriceCandles> findFirstBySeq(Long seq);

    Optional<PriceCandles> findBySeqAndStockId(Long seq, Long stockId);

    // symbol에 unique index가 있어 join으로 합쳐도 성능상 문제없음. 종목 존재 여부를 구분된 예외로 주기 위해 2단계 조회 유지.
    @Query("SELECT c FROM PriceCandles c JOIN FETCH c.stock s WHERE c.seq = :seq AND s.id IN :stockIds")
    List<PriceCandles> findAllBySeqAndStockIdIn(Long seq, List<Long> stockIds);
}
