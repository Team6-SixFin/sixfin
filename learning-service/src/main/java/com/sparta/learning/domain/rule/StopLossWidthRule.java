package com.sparta.learning.domain.rule;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 매수가 대비 손절 폭이 적절한지 진단
@Component
public class StopLossWidthRule implements DiagnosisRule {
    private static final int RULE_VERSION = 1;

    // 손절폭 기준값(%)
    private static final BigDecimal MIN_WIDTH_RATE = new BigDecimal("2");
    private static final BigDecimal MAX_WIDTH_RATE = new BigDecimal("15");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 4;

    @Override
    public RuleCode getRuleCode(){
        return RuleCode.STOP_LOSS_WIDTH;
    }

    // ENTRY 단계의 최초 매수는 모두 판정 대상이다
    @Override
    public boolean supports(ExecutionSnapshot snapshot) {
        return true;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot) {
        // 손절가가 없으면 폭을 계산할 수 없다
        // 손절가 미설정 자체는 STOP_LOSS_SET이 경고하므로 여기서는 판정하지 않은 사실만 남김
        if (snapshot.getPlannedStopLossPrice() == null) {
            return notApplicable(snapshot);
        }

        BigDecimal widthRate = calculateWidthRate(
                snapshot.getExecutedPrice(),
                snapshot.getPlannedStopLossPrice()
        );

        boolean tooNarrow = widthRate.compareTo(MIN_WIDTH_RATE) < 0;
        boolean tooWide = widthRate.compareTo(MAX_WIDTH_RATE) > 0;
        boolean appropriate = !tooNarrow && !tooWide;

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(appropriate ? DiagnosisStatus.PASS : DiagnosisStatus.WARNING)
                .metricValue(widthRate)
                .thresholdValue(tooNarrow ? MIN_WIDTH_RATE : MAX_WIDTH_RATE)
                .metrics(buildMetrics(snapshot, widthRate))
                .evidence(buildEvidence(widthRate, tooNarrow, tooWide))
                .build();
    }

    // 판정하지 못했다는 이력을 남긴다
    // 측정하지 못했으므로 metricValue와 thresholdValue는 비워 둔다
    private DiagnosisResult notApplicable(ExecutionSnapshot snapshot) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());

        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", "계획 손절가가 없어 손절 폭을 판정하지 않았습니다.");

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(DiagnosisStatus.NOT_APPLICABLE)
                .metrics(metrics)
                .evidence(evidence)
                .build();
    }

    // 손절 폭 = (매수가 - 손절가) / 매수가 * 100
    private BigDecimal calculateWidthRate(BigDecimal executedPrice, BigDecimal stopLossPrice) {
        return executedPrice.subtract(stopLossPrice)
                .divide(executedPrice, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    private ObjectNode buildMetrics(ExecutionSnapshot snapshot, BigDecimal widthRate) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());
        metrics.put("plannedStopLossPrice", snapshot.getPlannedStopLossPrice());
        metrics.put("stopLossWidthRate", widthRate);
        metrics.put("minWidthRate", MIN_WIDTH_RATE);
        metrics.put("maxWidthRate", MAX_WIDTH_RATE);
        return metrics;
    }

    private ObjectNode buildEvidence(BigDecimal widthRate, boolean tooNarrow, boolean tooWide) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(widthRate, tooNarrow, tooWide));
        return evidence;
    }

    private String buildMessage(BigDecimal widthRate, boolean tooNarrow, boolean tooWide) {
        String widthText = widthRate.setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (tooNarrow) {
            return "손절 폭이 " + widthText + "%로 좁습니다. 일반적인 가격 변동에도 손절이 실행될 수 있습니다.";
        }
        if (tooWide) {
            return "손절 폭이 " + widthText + "%로 넓습니다. 손실을 제한하는 손절의 목적에서 벗어날 수 있습니다.";
        }
        return "손절 폭이 " + widthText + "%로 적절한 범위입니다.";
    }
}
