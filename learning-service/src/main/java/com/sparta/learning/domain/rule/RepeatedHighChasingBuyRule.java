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

// 추가 매수가 고점 부근에서 이뤄졌는지 진단
// HIGH_CHASING_BUY와 같은 기준을 쓰되 최초 매수가 아닌 추가 매수를 판정한다
// 이전에도 경고가 있었는지는 판정 조건이 아니라 사용자에게 전할 맥락으로만 사용한다
@Component
public class RepeatedHighChasingBuyRule implements DiagnosisRule {

    private static final int RULE_VERSION = 1;

    // 20일 최고가 대비 매수가 비율 기준 (%)
    private static final BigDecimal HIGH_PRICE_RATIO_THRESHOLD = new BigDecimal("99");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 4;

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.REPEATED_HIGH_CHASING_BUY;
    }

    // TRADE 단계에는 매도도 들어오므로 추가 매수인지 확인한다
    @Override
    public boolean supports(DiagnosisContext context) {
        return context.executionSnapshot().getTradeType() == TradeType.BUY;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        ExecutionSnapshot snapshot = context.executionSnapshot();

        // 20일 최고가는 시세 조회 시점 정보라 값이 없을 수 있다
        // 비율 계산의 분모이므로 0 이하도 판정 대상에서 제외
        BigDecimal recent20High = snapshot.getRecent20dHigh();
        if (recent20High == null || recent20High.compareTo(BigDecimal.ZERO) <= 0) {
            return notApplicable(snapshot);
        }

        BigDecimal highPriceRatio = calculateHighPriceRatio(snapshot.getExecutedPrice(), recent20High);

        // 이번 매수가 고점 부근이면 경고한다
        // 이전 경고 유무를 조건에 넣으면 최초 매수가 고점이 아니었을 때
        // 이후 추가 매수가 모두 고점이어도 판정되지 않는 구간이 생긴다
        boolean chasing = highPriceRatio.compareTo(HIGH_PRICE_RATIO_THRESHOLD) >= 0;

        // 반복 여부는 판정 조건이 아니라 사용자에게 전할 맥락으로만 사용한다
        boolean chasedBefore = context.hasPreviousResult(
                RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING)
                || context.hasPreviousResult(
                RuleCode.REPEATED_HIGH_CHASING_BUY, DiagnosisStatus.WARNING);

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(chasing ? DiagnosisStatus.WARNING : DiagnosisStatus.PASS)
                .metricValue(highPriceRatio)
                .thresholdValue(HIGH_PRICE_RATIO_THRESHOLD)
                .metrics(buildMetrics(snapshot, highPriceRatio, chasedBefore))
                .evidence(buildEvidence(highPriceRatio, chasing, chasedBefore))
                .build();
    }

    // 판정하지 못했다는 이력을 남긴다 (측정하지 못했으므로 metricValue와 thresholdValue는 비워둠)
    private DiagnosisResult notApplicable(ExecutionSnapshot snapshot) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());

        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", "최근 20일 최고가 정보가 없어 고점 추격 반복 여부를 판정하지 않았습니다.");

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

    // 20일 최고가 대비 매수가 비율 = 매수가 / 20일 최고가 * 100
    private BigDecimal calculateHighPriceRatio(BigDecimal executedPrice, BigDecimal recent20dHigh) {
        return executedPrice
                .divide(recent20dHigh, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }

    private ObjectNode buildMetrics(ExecutionSnapshot snapshot, BigDecimal highPriceRatio, boolean chasedBefore) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());
        metrics.put("recent20dHigh", snapshot.getRecent20dHigh());
        metrics.put("highPriceRatio", highPriceRatio);
        metrics.put("thresholdRatio", HIGH_PRICE_RATIO_THRESHOLD);
        // 반복 판정의 근거가 된 이전 경고 여부
        metrics.put("chasedBefore", chasedBefore);
        return metrics;
    }

    private ObjectNode buildEvidence(BigDecimal highPriceRatio, boolean chasing, boolean chasedBefore) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(highPriceRatio, chasing, chasedBefore));
        return evidence;
    }

    // 판정은 같은 WARNING이라도 반복인지 아닌지에 따라 전할 내용이 다르다
    private String buildMessage(BigDecimal highPriceRatio, boolean chasing, boolean chasedBefore) {
        String ratioText = highPriceRatio.setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (chasing && chasedBefore) {
            return "이번 추가 매수도 최근 20일 최고가의 " + ratioText + "% 수준입니다. "
                    + "고점 부근 매수가 반복되면 평균 단가가 높아져 손실 구간이 넓어집니다.";
        }
        if (chasing) {
            return "추가 매수가 최근 20일 최고가의 " + ratioText + "% 수준입니다. "
                    + "고점 부근 매수는 손절 폭이 넓어지기 쉽습니다.";
        }
        return "추가 매수가 최근 20일 최고가의 " + ratioText + "% 수준으로 고점과 거리가 있습니다.";
    }
}
