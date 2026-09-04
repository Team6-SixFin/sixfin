package com.sparta.learning.domain.rule;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 포지션 전체에서 고점 추격 매수가 반복됐는지 진단 (매수 시점마다 판정한 고점 추격 결과를 집계)
@Component
public class HighChasingFrequencyRule implements DiagnosisRule {

    private static final int RULE_VERSION = 1;

    // 고점 추격 매수 횟수 기준
    // 한두 번은 우연일 수 있으나 반복되면 습관으로 본다
    private static final long FREQUENCY_THRESHOLD = 3;

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.HIGH_CHASING_FREQUENCY;
    }

    // CLOSE 단계에는 포지션 종료만 들어오므로 구분할 대상이 없다
    @Override
    public boolean supports(DiagnosisContext context) {
        return true;
    }

    // 고점 추격이 한 번도 없으면 0회로 PASS가 되므로 판정하지 못하는 경우가 없다
    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        ClosedPositionSnapshot snapshot = context.closedPositionSnapshot();

        // 매수 시점마다 이미 판정한 결과를 센다
        // 스냅샷에는 매수별 20일 최고가가 없어 다시 계산할 수도 없다
        //
        // 최초 매수는 HIGH_CHASING_BUY, 추가 매수는 REPEATED_HIGH_CHASING_BUY로 서로 다른 규칙이 기록하므로 둘을 합산해야 실제 고점 매수 횟수가 된다
        long chasingCount =
                context.countPreviousResults(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING)
                        + context.countPreviousResults(RuleCode.REPEATED_HIGH_CHASING_BUY, DiagnosisStatus.WARNING);
        boolean repeated = chasingCount >= FREQUENCY_THRESHOLD;

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.ofPosition(getRuleCode(), RULE_VERSION, snapshot.getPositionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .closedPositionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(repeated ? DiagnosisStatus.WARNING : DiagnosisStatus.PASS)
                .metricValue(BigDecimal.valueOf(chasingCount))
                .thresholdValue(BigDecimal.valueOf(FREQUENCY_THRESHOLD))
                .metrics(buildMetrics(snapshot, chasingCount))
                .evidence(buildEvidence(chasingCount, repeated))
                .build();
    }

    private ObjectNode buildMetrics(ClosedPositionSnapshot snapshot, long chasingCount) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("highChasingCount", chasingCount);
        metrics.put("thresholdCount", FREQUENCY_THRESHOLD);
        metrics.put("averageEntryPrice", snapshot.getAverageEntryPrice());
        metrics.put("realizedReturnRate", snapshot.getRealizedReturnRate());
        return metrics;
    }

    private ObjectNode buildEvidence(long chasingCount, boolean repeated) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(chasingCount, repeated));
        return evidence;
    }

    private String buildMessage(long chasingCount, boolean repeated) {
        if (repeated) {
            return "이 포지션에서 고점 부근 매수가 " + chasingCount + "번 있었습니다. "
                    + "매수 시점을 나누면 평균 단가를 낮출 수 있습니다.";
        }
        if (chasingCount == 0) {
            return "이 포지션에서는 고점 부근 매수가 없었습니다.";
        }
        return "이 포지션의 고점 부근 매수는 " + chasingCount + "번으로 반복 수준은 아닙니다.";
    }
}
