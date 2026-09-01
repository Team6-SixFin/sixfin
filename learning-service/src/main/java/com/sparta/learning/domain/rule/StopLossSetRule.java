package com.sparta.learning.domain.rule;


import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// 매수 시 계획 손절가를 설정했는지 진단 (최초 매수 1회 기록후 변경할 수 없으므로 추가 매수에서 판정 x)
@Component
public class StopLossSetRule implements DiagnosisRule {
    private static final int RULE_VERSION = 1;

    @Override
    public RuleCode getRuleCode() {
        return RuleCode.STOP_LOSS_SET;
    }

    // ENTRY 단계에서는 추가 조건이 필요없다
    @Override
    public boolean supports(ExecutionSnapshot snapshot){
        return true;
    }

    @Override
    public DiagnosisResult diagnose(ExecutionSnapshot snapshot){
        BigDecimal stopLossPrice = snapshot.getPlannedStopLossPrice();
        boolean hasStopLoss = stopLossPrice != null;

        return DiagnosisResult.builder()
                .diagnosisKey(DiagnosisKey.of(getRuleCode(), RULE_VERSION, snapshot.getExecutionId()))
                .userId(snapshot.getUserId())
                .positionId(snapshot.getPositionId())
                .executionSnapshot(snapshot)
                .diagnosisPhase(getRuleCode().getDiagnosisPhase())
                .ruleCode(getRuleCode().name())
                .ruleVersion(RULE_VERSION)
                .result(hasStopLoss ? DiagnosisStatus.PASS : DiagnosisStatus.WARNING)
                .metrics(buildMetrics(snapshot, stopLossPrice))
                .evidence(buildEvidence(hasStopLoss))
                .build();
    }

    // 판정에 사용된 값들을 담는다
    // 손절가가 없으면 담을 값이 없으므로 키를 넣지 않는다
    private ObjectNode buildMetrics(ExecutionSnapshot snapshot, BigDecimal stopLossPrice){
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("executedPrice", snapshot.getExecutedPrice());
        if (stopLossPrice != null) {
            metrics.put("plannedStopLossPrice", stopLossPrice);
        }
        return metrics;
    }

    // 사용자에게 보여줄 판정 근거 생성
    private ObjectNode buildEvidence(boolean hasStopLoss){
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        evidence.put("message", hasStopLoss
                ? "매수 전에 계획 손절가를 설정했습니다."
                : "이번 매수에는 계획 손절가가 입력되지 않았습니다.");
        return evidence;
    }
}
