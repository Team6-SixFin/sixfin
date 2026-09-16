package com.sparta.learning.application.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

// AI 입력 데이터를 담기 위한 요청용 DTO
public record AiFeedbackRequestDto(
        String feedbackType,
        String promptVersion,
        UUID userId,
        UUID positionId,
        StockDto stock,
        PositionDto position,
        ClosedInfoDto closedInfo,
        String previousFeedbackSummary,

        // [신규] 무엇이 생략됐는지 AI에게 알린다. 이게 없으면 AI가 "총 20회 매매하셨네요"라고 환각한다.
        ContextScopeDto contextScope,

        // [신규] 생략된 행의 정보를 숫자로 보존한다.
        ExecutionSummaryDto executionSummary,

        // [의미 변경] 전체 체결 → 선별된 대표 체결만 (최초 / 최근 N / 최대 매수 / 최대 매도)
        List<ExecutionDto> executions,

        MarketContextDto marketContext,

        // [신규] 규칙 × 결과별 집계. 어떤 위반도 여기서 사라지지 않는다.
        List<DiagnosisSummaryDto> diagnosisSummary,

        // [의미 변경] 전체 진단 → 규칙 × 결과별 대표 1건씩 (최대 32건)
        List<DiagnosisDto> diagnoses
) {
    public record StockDto(Long stockId, String symbol, String name) {}

    public record PositionDto(String status, BigDecimal averageEntryPrice, Integer quantity, BigDecimal plannedStopLossPrice) {}

    /**
     * [변경] executionId(UUID, 키 포함 약 53B) 제거. AI가 활용할 방법이 없었다.
     *
     * [주의] 순번(seq) 필드는 두지 않는다.
     * 대표 체결만 선별하는 구조라 "전체에서 몇 번째 체결인지"를 알 수 없고,
     * 선별 리스트 안의 인덱스를 seq로 주면 1,000건 포지션에서 23번까지만 나와
     * contextScope.totalExecutionCount와 모순된다. 순서는 executedAt으로 충분하다.
     *
     * [추가] positionAveragePrice
     * AI입력명세 ON_DEMAND에 요구하는 "평균 매수가 변화"의 근거다.
     * 기존 DTO에 빠져 있어 전달되지 않던 항목이며, 축소본은 20여 행뿐이라
     * 행마다 실어도 약 0.6KB로 크기 영향이 거의 없고 추이는 오히려 더 선명해진다.
     */
    public record ExecutionDto(
            String tradeType,
            Integer quantity,
            BigDecimal executedPrice,
            Integer positionQuantityAfter,
            BigDecimal positionAveragePrice,
            String investmentReason,
            String executedAt,
            String role   // FIRST / RECENT / LARGEST_BUY / LARGEST_SELL
    ) {}

    public record MarketContextDto(BigDecimal recent20DayHigh, BigDecimal recent20DayLow, BigDecimal recent5DayReturnRate, String quoteAt) {}

    /**
     * [유지] metrics를 그대로 둔다.
     *
     * 규칙 8종을 대조한 결과, metrics에서 metricValue/thresholdValue와 중복되는 것은
     * 파생 지표뿐이고(진단 1건당 약 43B), 아래 값들은 컬럼에 없는 고유 정보다.
     *   - executedPrice, recent20dHigh         (HIGH_CHASING_BUY)
     *   - chasedBefore                         (REPEATED_HIGH_CHASING_BUY)
     *   - minWidthRate, maxWidthRate           (STOP_LOSS_WIDTH — 적정 범위 안내의 근거)
     *   - averageExitPrice, realizedReturnRate (STOP_LOSS_ADHERENCE)
     *   - belowStopLossRate                    (SELL_BELOW_STOP_LOSS)
     * 특히 StopLossSetRule은 metricValue를 설정하지 않아 metrics가 유일한 수치 근거다.
     *
     * diagnoses가 최대 32건으로 상한이 잡혀 있어 metrics 제거로 아낄 수 있는 양은 약 1.4KB에 불과하다.
     * 정보 손실이 더 비싸다.
     *
     * metricValue / thresholdValue / metrics는 모두 "대표 행 1건"의 값이다.
     * 그룹 최대·최소는 diagnosisSummary 쪽에 있다.
     */
    public record DiagnosisDto(
            String ruleCode,
            Integer ruleVersion,
            String result,
            BigDecimal metricValue,
            BigDecimal thresholdValue,
            JsonNode metrics,
            String evidenceMessage
    ) {}

    /** [신규] 컨텍스트가 요약본임을 명시한다. */
    public record ContextScopeDto(
            boolean truncated,
            long totalExecutionCount,
            int includedExecutionCount,
            long totalDiagnosisCount,
            int includedDiagnosisCount,
            String note
    ) {}

    /**
     * [신규] 체결 전체 집계.
     *
     * POSITION_REVIEW에서는 closedInfo와 겹치는 필드가 null로 내려간다.
     * Trading이 확정한 집계(closedInfo)와 Learning이 스냅샷으로 재계산한 값이 어긋나면
     * AI가 모순된 숫자 두 개를 받기 때문이다. 자세한 이유는 AiContextAssembler 참고.
     */
    public record ExecutionSummaryDto(
            long buyCount,
            long sellCount,
            Long totalBuyQuantity,
            Long totalSellQuantity,
            BigDecimal averageBuyPrice,
            BigDecimal averageSellPrice,
            BigDecimal highestBuyPrice,
            BigDecimal lowestBuyPrice,
            BigDecimal highestSellPrice,
            BigDecimal lowestSellPrice,
            BigDecimal realizedProfit,
            String firstExecutedAt,
            String lastExecutedAt
    ) {}

    /** [신규] 규칙별 진단 집계. occurrenceCount가 "반복 여부" 판단의 근거가 된다. */
    public record DiagnosisSummaryDto(
            String ruleCode,
            String result,
            long occurrenceCount,
            BigDecimal maxMetricValue,
            BigDecimal minMetricValue,
            BigDecimal thresholdValue
    ) {}

    // [리뷰 반영]: 시스템이 계산한 손익 정보 제공용
    public record ClosedInfoDto(
            BigDecimal averageExitPrice,
            Long totalBoughtQuantity,
            Long totalSoldQuantity,
            BigDecimal realizedProfit,
            BigDecimal realizedReturnRate,
            String openedAt,
            String closedAt
    ) {}
}