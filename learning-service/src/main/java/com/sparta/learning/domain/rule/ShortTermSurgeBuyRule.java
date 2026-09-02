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

// 최근 단기 상승 이후에 매수했는지 진단
@Component
public class ShortTermSurgeBuyRule implements DiagnosisRule {

    private static final int RULE_VERSION = 1;

    // 최근 5거래일 수익률 기준 (%)
    private static final BigDecimal SURGE_RATE_THRESHOLD = new BigDecimal("10");

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.SHORT_TERM_SURGE_BUY;
    }

    // 5거래일 수익률은 시세 조회 시점 정보라 값이 없을 수 있다
    // 음수(하락)도 정상적인 판정 대상이므로 null만 제외
    @Override
    public boolean supports(ExecutionSnapshot snapshot) {
        return snapshot.getRecent5dReturnRate() != null;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot) {
        BigDecimal returnRate = snapshot.getRecent5dReturnRate();
        boolean surged = returnRate.compareTo(SURGE_RATE_THRESHOLD) >= 0; // 10퍼센트 이상 급증했는지

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(surged ? DiagnosisStatus.WARNING : DiagnosisStatus.PASS)
                .metricValue(returnRate)
                .thresholdValue(SURGE_RATE_THRESHOLD)
                .metrics(buildMetrics(snapshot, returnRate))
                .evidence(buildEvidence(returnRate, surged))
                .build();
    }

    private ObjectNode buildMetrics(ExecutionSnapshot snapshot, BigDecimal returnRate) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());
        metrics.put("recent5dReturnRate", returnRate);
        metrics.put("thresholdRate", SURGE_RATE_THRESHOLD);
        return metrics;
    }

    private ObjectNode buildEvidence(BigDecimal returnRate, boolean surged) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(returnRate, surged));
        return evidence;
    }

    private String buildMessage(BigDecimal returnRate, boolean surged) {
        String rateText = returnRate.setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (surged) {
            return "최근 5거래일 동안 " + rateText + "% 상승한 뒤 매수했습니다. " + "단기 과열 구간의 매수는 조정 시 손실이 커질 수 있습니다.";
        }
        return "최근 5거래일 수익률은 " + rateText + "%로 단기 과열 구간은 아닙니다.";
    }
}
