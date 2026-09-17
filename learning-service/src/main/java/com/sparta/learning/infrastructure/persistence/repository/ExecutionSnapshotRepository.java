package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.application.dto.result.PositionStockInfo;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.infrastructure.persistence.projection.ExecutionAggregateProjection;
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

    /**
     * 포지션 하나의 종목 정보를 최초 체결에서 조회하고 소유권 검증도 한다
     * 종목 정보 세 값만 필요한 호출에서 전체 엔티티를 받지 않기 위해 분리했다.
     */
    @Query(value = """
            select s.position_id  as positionId,
                   s.stock_symbol as stockSymbol,
                   s.stock_name   as stockName
              from execution_snapshots s
             where s.position_id = :positionId
               and s.user_id = :userId
             order by s.executed_at, s.id
             limit 1
            """, nativeQuery = true)
    Optional<PositionStockInfo> findStockInfoByPositionIdAndUserId(
            @Param("positionId") UUID positionId,
            @Param("userId") UUID userId
    );

    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(UUID positionId, UUID userId);

    Optional<ExecutionSnapshot> findByConsumedEventEventId(UUID eventId);
    // 요청형 피드백을 위한 최신(마지막) 체결 조회용
    Optional<ExecutionSnapshot> findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(UUID positionId, UUID userId);

    // 요청형 피드백을 전체 체결 내역 조회
    List<ExecutionSnapshot> findAllByPositionIdOrderByExecutedAtAscIdAsc(UUID positionId);

    // ===== [신규] 컨텍스트 축소용 =====
    /**
     * AI 컨텍스트에 실을 "대표 체결"만 왕복 1회로 조회한다.
     *   - 최초 체결 1건      : 진입 근거와 계획 손절가의 원본
     *   - 최근 N건          : 현재 행동 패턴
     *   - 최대 수량 매수 1건 : 포지션에 가장 큰 영향을 준 매수
     *   - 최대 수량 매도 1건 : 손절/익절 판단의 핵심 근거
     *
     * 왜 UNION인가:
     * 네 조건을 각각 쿼리하면 왕복 4회다. UNION은 각 분기가 독립적으로
     * (position_id, executed_at) 인덱스 + LIMIT을 쓰므로 정렬 대상이 N건으로 제한되고 왕복은 1회다.
     * 동일 행이 여러 분기에 걸려도 UNION이 중복을 제거한다.
     *
     * 왜 CTE(WITH)로 묶지 않았는가:
     * 여러 번 참조되는 CTE는 PostgreSQL이 materialize 하므로 1,000행을 먼저 다 뽑아버린다.
     * 그러면 각 분기의 LIMIT이 인덱스를 못 탄다. 분기를 그대로 둬야 인덱스가 산다.
     *
     * [전제] idx_execution_snapshot_position_executed (position_id, executed_at, id) 가 있어야 한다.
     *        없으면 각 분기가 그 포지션의 전체 행을 힙에서 읽어 정렬하므로,
     *        같은 1,000행을 네 번 훑게 되어 개선 전보다 DB 작업량이 오히려 늘어난다.
     */
    @Query(value = """
            (
                SELECT * FROM execution_snapshots
                 WHERE position_id = :positionId
                 ORDER BY executed_at ASC, id ASC
                 LIMIT 1
            )
            UNION
            (
                SELECT * FROM execution_snapshots
                 WHERE position_id = :positionId
                 ORDER BY executed_at DESC, id DESC
                 LIMIT :recentLimit
            )
            UNION
            (
                SELECT * FROM execution_snapshots
                 WHERE position_id = :positionId AND trade_type = 'BUY'
                 ORDER BY quantity DESC, executed_at ASC, id ASC
                 LIMIT 1
            )
            UNION
            (
                SELECT * FROM execution_snapshots
                 WHERE position_id = :positionId AND trade_type = 'SELL'
                 ORDER BY quantity DESC, executed_at ASC, id ASC
                 LIMIT 1
            )
            ORDER BY executed_at ASC, id ASC
            """, nativeQuery = true)
    List<ExecutionSnapshot> findContextExecutions(@Param("positionId") UUID positionId,
                                                  @Param("recentLimit") int recentLimit);

    /**
     * 체결 전체를 1행으로 집계한다. 축소로 사라진 행의 정보를 숫자로 보존하는 역할.
     *
     * 트레이드오프(정직하게):
     * 이 쿼리는 여전히 position_id에 해당하는 전체 행을 스캔한다. DB의 "스캔 비용"은 줄지 않는다.
     * 줄어드는 것은 (1) 전송 행 수 (2) JPA 엔티티 하이드레이션
     * (3) 영속성 컨텍스트 크기와 dirty checking (4) JSON 직렬화 (5) AI 입력 토큰 이다.
     * 지배적 비용은 (5) > (2) > (3) > (1) 순서다.
     */
    @Query(value = """
            SELECT
                COUNT(*)                                                            AS "totalCount",
                COUNT(*) FILTER (WHERE trade_type = 'BUY')                          AS "buyCount",
                COUNT(*) FILTER (WHERE trade_type = 'SELL')                         AS "sellCount",
                COALESCE(SUM(quantity) FILTER (WHERE trade_type = 'BUY'), 0)        AS "totalBuyQuantity",
                COALESCE(SUM(quantity) FILTER (WHERE trade_type = 'SELL'), 0)       AS "totalSellQuantity",
                SUM(executed_price * quantity) FILTER (WHERE trade_type = 'BUY')    AS "totalBuyAmount",
                SUM(executed_price * quantity) FILTER (WHERE trade_type = 'SELL')   AS "totalSellAmount",
                MAX(executed_price) FILTER (WHERE trade_type = 'BUY')               AS "highestBuyPrice",
                MIN(executed_price) FILTER (WHERE trade_type = 'BUY')               AS "lowestBuyPrice",
                MAX(executed_price) FILTER (WHERE trade_type = 'SELL')              AS "highestSellPrice",
                MIN(executed_price) FILTER (WHERE trade_type = 'SELL')              AS "lowestSellPrice",
                COALESCE(SUM(execution_realized_profit), 0)                         AS "realizedProfit",
                to_char(MIN(executed_at) AT TIME ZONE 'UTC',
                        'YYYY-MM-DD"T"HH24:MI"Z"')                                  AS "firstExecutedAt",
                to_char(MAX(executed_at) AT TIME ZONE 'UTC',
                        'YYYY-MM-DD"T"HH24:MI"Z"')                                  AS "lastExecutedAt"
            FROM execution_snapshots
            WHERE position_id = :positionId
            """, nativeQuery = true)
    ExecutionAggregateProjection aggregateByPositionId(@Param("positionId") UUID positionId);
}
