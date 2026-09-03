package com.sparta.learning.domain.rule;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.domain.model.TradeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/* 계획 손절가보다 낮은 가격에 매도했는지 진단 */
@Component
public class SellBelowStopLossRule implements DiagnosisRule {

    private static final int RULE_VERSION = 1;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 4;

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.SELL_BELOW_STOP_LOSS;
    }

    // TRADE 단계에는 추가 매수도 들어오므로 매도인지 확인한다
    @Override
    public boolean supports(ExecutionSnapshot snapshot) {
        return snapshot.getTradeType() == TradeType.SELL;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot) {
        BigDecimal executedPrice = snapshot.getExecutedPrice();
        BigDecimal stopLossPrice = snapshot.getPlannedStopLossPrice();

        // 계획 손절가가 없으면 준수 여부를 비교할 기준이 없다
        // 손절가 미설정 자체는 STOP_LOSS_SET이 경고하므로 여기서는 판정하지 않은 사실만 남긴다
        if (stopLossPrice == null) {
            return notApplicable(snapshot);
        }

        boolean violated = executedPrice.compareTo(stopLossPrice) < 0;

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(violated ? DiagnosisStatus.VIOLATION : DiagnosisStatus.PASS)
                .metricValue(executedPrice)
                .thresholdValue(stopLossPrice)
                .metrics(buildMetrics(executedPrice, stopLossPrice, violated))
                .evidence(buildEvidence(executedPrice, stopLossPrice, violated))
                .build();
    }

    // 판정하지 못했다는 이력을 남긴다
    // 측정하지 못했으므로 metricValue와 thresholdValue는 비워 둔다
    private DiagnosisResult notApplicable(ExecutionSnapshot snapshot) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());

        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", "계획 손절가가 없어 손절 원칙 준수 여부를 판정하지 않았습니다.");

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

    private ObjectNode buildMetrics(BigDecimal executedPrice, BigDecimal stopLossPrice, boolean violated) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", executedPrice);
        metrics.put("plannedStopLossPrice", stopLossPrice);
        // 손절가를 얼마나 밑돌았는지는 위반한 경우에만 의미가 있다
        if (violated) {
            metrics.put("belowStopLossRate", calculateBelowRate(executedPrice, stopLossPrice));
        }
        return metrics;
    }

    // 손절가 대비 하회 폭 = (손절가 - 매도가) / 손절가 * 100
    private BigDecimal calculateBelowRate(BigDecimal executedPrice, BigDecimal stopLossPrice) {
        return stopLossPrice.subtract(executedPrice)
                .divide(stopLossPrice, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    private ObjectNode buildEvidence(BigDecimal executedPrice, BigDecimal stopLossPrice, boolean violated) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(executedPrice, stopLossPrice, violated));
        return evidence;
    }

    private String buildMessage(BigDecimal executedPrice, BigDecimal stopLossPrice, boolean violated) {
        String executedText = toDisplayText(executedPrice);
        String stopLossText = toDisplayText(stopLossPrice);

        if (violated) {
            return "계획 손절가 " + stopLossText + "달러보다 낮은 " + executedText + "달러에 매도했습니다. "
                    + "손절 시점을 놓치면 손실이 계획보다 커집니다.";
        }
        return "계획 손절가 " + stopLossText + "달러 이상인 " + executedText + "달러에 매도했습니다.";
    }

    private String toDisplayText(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
