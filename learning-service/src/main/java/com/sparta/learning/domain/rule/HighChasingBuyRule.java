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

    // 20일 최고가가 있어야 비율을 계산할 수 있다
    @Override
    public boolean supports(ExecutionSnapshot snapshot) {
        BigDecimal recent20High = snapshot.getRecent20dHigh();
        return recent20High != null && recent20High.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot){
        BigDecimal highPriceRatio = calculateHighPriceRatio(
          snapshot.getExecutedPrice(),
          snapshot.getRecent20dHigh()
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
