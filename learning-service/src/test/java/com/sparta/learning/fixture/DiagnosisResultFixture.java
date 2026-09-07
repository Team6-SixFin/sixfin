package com.sparta.learning.fixture;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// CLOSE 규칙이 집계할 이전 진단 결과를 만듭니다
// 판정 결과와 규칙 코드만 사용하므로 나머지 값은 기본값으로 채웁니다
public final class DiagnosisResultFixture {

    private DiagnosisResultFixture() {
    }

    public static DiagnosisResult of(RuleCode ruleCode, DiagnosisStatus status) {
        return DiagnosisResult.builder()
                .diagnosisKey(ruleCode.name() + ":" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .diagnosisPhase(ruleCode.getDiagnosisPhase())
                .ruleCode(ruleCode.name())
                .ruleVersion(1)
                .result(status)
                .metrics(JsonNodeFactory.instance.objectNode())
                .evidence(JsonNodeFactory.instance.objectNode())
                .build();
    }

    // 같은 규칙과 결과를 여러 건 만든다. 집계 횟수 검증에 사용한다
    public static List<DiagnosisResult> listOf(RuleCode ruleCode, DiagnosisStatus status, int count) {
        List<DiagnosisResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(of(ruleCode, status));
        }
        return results;
    }
}
