package com.sparta.trading.domain.repository;

import com.sparta.trading.domain.entity.MarketClock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MarketClockRepository extends JpaRepository<MarketClock, Integer> {

    /** 쓰기 트랜잭션에서 단일 행을 비관적 락으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MarketClock m where m.id = 1")
    Optional<MarketClock> findForUpdate();
}
