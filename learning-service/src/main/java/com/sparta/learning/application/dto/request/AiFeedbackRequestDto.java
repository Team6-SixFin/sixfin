package com.sparta.learning.application.dto.request;

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
        List<ExecutionDto> executions,
        MarketContextDto marketContext,
        List<DiagnosisDto> diagnoses
) {
    public record StockDto(Long stockId, String symbol, String name) {}
    public record PositionDto(String status, BigDecimal averageEntryPrice, Integer quantity, BigDecimal plannedStopLossPrice) {}
    public record ExecutionDto(UUID executionId, String tradeType, Integer quantity, BigDecimal executedPrice, Integer positionQuantityAfter, String investmentReason, String executedAt) {}
    public record MarketContextDto(BigDecimal recent20DayHigh, BigDecimal recent20DayLow, BigDecimal recent5DayReturnRate, String quoteAt) {}
    public record DiagnosisDto(String ruleCode, Integer ruleVersion, String result, BigDecimal metricValue, BigDecimal thresholdValue, Object metrics, Object evidence) {}
}