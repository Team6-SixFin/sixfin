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

/* 최근 20일 최고가 부근에서 매수했는지 진단*/
@Component
public class HighChasingBuyRule implements DiagnosisRule{

    private static final int RULE_VERSION = 1;

    // 20일 최고가 대비 매수가 비율 기준 (%)
    private static final BigDecimal HIGH_PRICE_RATIO_THRESHOLD = new BigDecimal("99");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 4;

    @Override
    public RuleCode getRuleCode(){
        return RuleCode.HIGH_CHASING_BUY;
    }

    // ENTRY 단계의 최초 매수는 모두 판정 대상이다
    @Override
    public boolean supports(ExecutionSnapshot snapshot) {
        return true;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot){
        // 20일 최고가는 시세 조회 시점 정보라 값이 없을 수 있다
        // 비율 계산의 분모이므로 0 이하도 판정 대상에서 제외한다
        BigDecimal recent20High = snapshot.getRecent20dHigh();
        if (recent20High == null || recent20High.compareTo(BigDecimal.ZERO) <= 0) {
            return notApplicable(snapshot);
        }

        BigDecimal highPriceRatio = calculateHighPriceRatio(
          snapshot.getExecutedPrice(),
          recent20High
        );
        boolean chasing = highPriceRatio.compareTo(HIGH_PRICE_RATIO_THRESHOLD) >= 0;

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
                .metrics(buildMetrics(snapshot, highPriceRatio))
                .evidence(buildEvidence(highPriceRatio, chasing))
                .build();
    }

    // 판정하지 못했다는 이력을 남긴다
    // 측정하지 못했으므로 metricValue와 thresholdValue는 비워 둔다
    private DiagnosisResult notApplicable(ExecutionSnapshot snapshot) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());

        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", "최근 20일 최고가 정보가 없어 고점 대비 매수 위치를 판정하지 않았습니다.");

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

    private BigDecimal calculateHighPriceRatio(BigDecimal executedPrice, BigDecimal recent20dHigh){
        return executedPrice.divide(recent20dHigh, CALCULATION_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private ObjectNode buildMetrics(ExecutionSnapshot snapshot, BigDecimal highPriceRatio){
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());
        metrics.put("recent20dHigh", snapshot.getRecent20dHigh());
        metrics.put("highPriceRatio", highPriceRatio);
        metrics.put("thresholdRatio", HIGH_PRICE_RATIO_THRESHOLD);
        return metrics;
    }

    private ObjectNode buildEvidence(BigDecimal highPriceRatio, boolean chasing){
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(highPriceRatio, chasing));
        return evidence;
    }
    private String buildMessage(BigDecimal highPriceRatio, boolean chasing){
        String ratioText = highPriceRatio.setScale(2, RoundingMode.HALF_UP).toPlainString();

        if(chasing){
            return "매수가가 최근 20일 최고가의 " + ratioText + "% 수준입니다. "
                    + "고점 부근 매수는 손절 폭이 넓어지기 쉽습니다.";
        }
        return "매수가가 최근 20일 최고가의 " + ratioText + "% 수준으로 고점과 거리가 있습니다.";
    }

}
