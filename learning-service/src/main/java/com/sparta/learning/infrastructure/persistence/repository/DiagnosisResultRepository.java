package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.infrastructure.persistence.projection.DiagnosisSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiagnosisResultRepository extends JpaRepository<DiagnosisResult, Long> {
    // 이미 저장된 진단의 키를 찾는다
    // 규칙마다 조회하지 않게 이번에 만튼 키를 한 번에 넘겨 조회 횟수를 줄임
    List<DiagnosisResult> findByDiagnosisKeyIn(Collection<String> diagnosisKeys);

    // 같은 포지션의 이전 진단 결과를 저장 순서대로 조회한다
    // 반복 여부와 위반 건수를 판정하는 규칙이 사용하며, DiagnosisContext에 담아 전달한다
    List<DiagnosisResult> findByPositionIdOrderByIdAsc(UUID positionId);

    // Ai 피드백 생성을 위한 진단 결과 조회용
    List<DiagnosisResult> findAllByPositionId(UUID positionId);

    // 학습 자료 추천 시 사용자 소유권을 함께 확인하고 최신 진단부터 조회한다
    List<DiagnosisResult> findAllByPositionIdAndUserIdOrderByIdDesc(UUID positionId, UUID userId);

    // ===== [신규] 컨텍스트 축소용 =====
    /**
     * 규칙 × 판정결과로 묶어 집계하면서, 각 그룹의 최신 진단 1건을 대표로 함께 가져온다.
     *
     * 왜 이 쿼리 하나로 충분한가:
     * RuleCode enum이 8종, DiagnosisStatus가 4종이므로 결과 행 수의 이론적 상한이 32다.
     * 포지션 체결이 10건이든 10,000건이든 진단 컨텍스트 크기가 변하지 않는다.
     * "최근 K건만 보내기" 방식과 달리 어떤 규칙 위반도 목록에서 사라지지 않는다.
     *
     * 실행 순서: WHERE → 윈도우 함수 → DISTINCT ON → ORDER BY
     * 따라서 COUNT/MAX/MIN OVER는 필터링된 전체 집합에서 계산된 뒤 그룹당 1행만 남는다.
     *
     * DISTINCT ON + 윈도우 함수는 PostgreSQL 전용이다.
     * 이 프로젝트는 jsonb에 이미 의존하고 있어 DB 교체 가능성이 없으므로 이식성 대신 성능을 택했다.
     *
     * [주의] d.metric_value(대표 행 값)와 MAX/MIN OVER(그룹 집계)를 별도 별칭으로 내보낸다.
     *        metrics가 대표 행의 값이므로 그 옆의 metricValue도 대표 행 값이어야 모순이 없다.
     */
    @Query(value = """
            SELECT DISTINCT ON (d.rule_code, d.result)
                   d.id                                                          AS "diagnosisId",
                   d.rule_code                                                   AS "ruleCode",
                   d.result                                                      AS "result",
                   d.rule_version                                                AS "ruleVersion",
                   d.metric_value                                                AS "metricValue",
                   d.threshold_value                                             AS "thresholdValue",
                   d.metrics::text                                               AS "metricsJson",
                   d.evidence ->> 'message'                                      AS "evidenceMessage",
                   COUNT(*)            OVER (PARTITION BY d.rule_code, d.result) AS "occurrenceCount",
                   MAX(d.metric_value) OVER (PARTITION BY d.rule_code, d.result) AS "maxMetricValue",
                   MIN(d.metric_value) OVER (PARTITION BY d.rule_code, d.result) AS "minMetricValue"
              FROM diagnosis_results d
             WHERE d.position_id = :positionId
               AND d.diagnosis_phase IN (:phases)
             ORDER BY d.rule_code, d.result, d.id DESC
            """, nativeQuery = true)
    List<DiagnosisSummaryProjection> summarizeByPositionIdAndPhases(
            @Param("positionId") UUID positionId,
            @Param("phases") Collection<String> phases);
}
