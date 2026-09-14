package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.result.PositionStockInfo;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionSnapshotRepository extends JpaRepository<ExecutionSnapshot, Long> {

    /** 피드백 목록에 붙일 종목 정보를 포지션당 최초 체결 한 행씩 조회한다 */
    @Query(value = """
            select distinct on (s.position_id)
                   s.position_id  as positionId,
                   s.stock_symbol as stockSymbol,
                   s.stock_name   as stockName
              from execution_snapshots s
             where s.position_id in (:positionIds)
             order by s.position_id, s.executed_at, s.id
            """, nativeQuery = true)
    List<PositionStockInfo> findStockInfoByPositionIds(@Param("positionIds") Collection<UUID> positionIds);

    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(UUID positionId, UUID userId);

    Optional<ExecutionSnapshot> findByConsumedEventEventId(UUID eventId);
    // 요청형 피드백을 위한 최신(마지막) 체결 조회용
    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(UUID positionId, UUID userId);

    // 요청형 피드백을 전체 체결 내역 조회
    List<ExecutionSnapshot> findAllByPositionIdOrderByExecutedAtAscIdAsc(UUID positionId);
}
