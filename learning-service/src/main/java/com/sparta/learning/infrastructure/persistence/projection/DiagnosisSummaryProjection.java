package com.sparta.learning.infrastructure.persistence.projection;

import java.math.BigDecimal;

/**
 * 규칙 × 판정결과별 집계 + 그 그룹의 대표 진단 1건.
 *
 * 왜 집계와 대표행을 한 쿼리에서 받는가:
 * GROUP BY로 개수만 받으면 evidence / metrics를 얻으려고 쿼리를 한 번 더 쳐야 한다.
 * DISTINCT ON + 윈도우 함수를 쓰면 왕복 1회로 둘 다 얻는다. (PostgreSQL 전용)
 *
 * [중요] "대표 행 값"과 "그룹 집계값"을 분리해서 담는다.
 * metrics는 대표 행 1건의 값이므로, 그 옆에 놓이는 metricValue도 대표 행 자신의 값이어야 한다.
 * 그룹 최대·최소를 섞으면 AI가 같은 지표에 대해 서로 다른 숫자 두 개를 보게 된다.
 */
public interface DiagnosisSummaryProjection {

    // ---------- 대표 행(1건) 고유 값 ----------

    /** 대표 진단 ID. feedback_diagnoses 연결에 그대로 사용한다. */
    Long getDiagnosisId();

    String getRuleCode();
    String getResult();
    Integer getRuleVersion();

    /** 대표 행 자신의 metric_value. StopLossSetRule처럼 설정하지 않는 규칙에서는 null이다. */
    BigDecimal getMetricValue();

    /** 대표 행 자신의 threshold_value. */
    BigDecimal getThresholdValue();

    /**
     * 대표 행의 metrics jsonb를 문자열로 받은 값.
     *
     * 왜 통째로 가져가는가:
     * 규칙 8종을 전수 대조한 결과, metrics에서 metricValue/thresholdValue와 중복되는 것은
     * highPriceRatio / thresholdRatio 같은 파생 지표뿐이고(진단 1건당 약 43B),
     * executedPrice / recent20dHigh / plannedStopLossPrice / chasedBefore /
     * minWidthRate / maxWidthRate 는 컬럼에 없는 고유 정보다.
     * 특히 StopLossSetRule은 metricValue를 설정하지 않아 metrics가 유일한 수치 근거다.
     */
    String getMetricsJson();

    /** evidence jsonb 전체가 아니라 -> 'message'만 꺼낸 문자열. 명세상 노출되는 것도 이 값뿐이다. */
    String getEvidenceMessage();

    // ---------- 그룹(rule_code × result) 집계값 ----------

    /** 같은 규칙·결과가 이 포지션에서 몇 번 나왔는지. 축소해도 "반복 횟수"는 이 값으로 보존된다. */
    long getOccurrenceCount();

    BigDecimal getMaxMetricValue();
    BigDecimal getMinMetricValue();
}