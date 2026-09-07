package com.sparta.learning.domain.rule;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 포지션 전체에서 손절 원칙을 지켰는지 진단
// 매도마다 판정한 SELL_BELOW_STOP_LOSS 결과를 집계한다
@Component
public class StopLossAdherenceRule implements DiagnosisRule {

    private static final int RULE_VERSION = 1;

    // 손절가보다 낮게 판 매도가 한 건이라도 있으면 원칙을 지키지 않은 것으로 본다
    private static final long VIOLATION_THRESHOLD = 1;

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.STOP_LOSS_ADHERENCE;
    }

    // CLOSE 단계에는 포지션 종료만 들어오므로 구분할 대상이 없다
    @Override
    public boolean supports(DiagnosisContext context) {
        return true;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext context) {
        ClosedPositionSnapshot snapshot = context.closedPositionSnapshot();

        // 계획 손절가가 없으면 준수 여부를 판단할 기준이 없다
        // 손절가 미설정 자체는 STOP_LOSS_SET이 경고하므로 여기서는 판정하지 않은 사실만 남긴다
        if (snapshot.getPlannedStopLossPrice() == null) {
            return notApplicable(snapshot);
        }

        // 매도 시점마다 이미 판정한 결과를 센다
        // 스냅샷을 다시 계산하면 판정 기준이 두 곳에 생겨 어긋날 수 있다
        long violationCount = context.countPreviousResults(
                RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.VIOLATION);
        boolean violated = violationCount >= VIOLATION_THRESHOLD;

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.ofPosition(getRuleCode(), RULE_VERSION, snapshot.getPositionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .closedPositionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(violated ? DiagnosisStatus.VIOLATION : DiagnosisStatus.PASS)
                .metricValue(BigDecimal.valueOf(violationCount))
                .thresholdValue(BigDecimal.valueOf(VIOLATION_THRESHOLD))
                .metrics(buildMetrics(snapshot, violationCount))
                .evidence(buildEvidence(violationCount, violated))
                .build();
    }

    // 판정하지 못했다는 이력을 남긴다
    // 측정하지 못했으므로 metricValue와 thresholdValue는 비워 둔다
    private DiagnosisResult notApplicable(ClosedPositionSnapshot snapshot) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("averageExitPrice", snapshot.getAverageExitPrice());

        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", "계획 손절가가 없어 손절 원칙 준수 여부를 판정하지 않았습니다.");

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.ofPosition(getRuleCode(), RULE_VERSION, snapshot.getPositionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .closedPositionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(DiagnosisStatus.NOT_APPLICABLE)
                .metrics(metrics)
                .evidence(evidence)
                .build();
    }

    private ObjectNode buildMetrics(ClosedPositionSnapshot snapshot, long violationCount) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("plannedStopLossPrice", snapshot.getPlannedStopLossPrice());
        metrics.put("averageExitPrice", snapshot.getAverageExitPrice());
        metrics.put("violationCount", violationCount);
        metrics.put("realizedReturnRate", snapshot.getRealizedReturnRate());
        return metrics;
    }

    private ObjectNode buildEvidence(long violationCount, boolean violated) {
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", buildMessage(violationCount, violated));
        return evidence;
    }

    private String buildMessage(long violationCount, boolean violated) {
        if (violated) {
            return "이 포지션에서 계획 손절가보다 낮게 매도한 거래가 " + violationCount + "건 있습니다. "
                    + "손절 시점을 놓치면 손실이 계획보다 커집니다.";
        }
        return "이 포지션의 매도는 모두 계획 손절가를 지켰습니다.";
    }
}
