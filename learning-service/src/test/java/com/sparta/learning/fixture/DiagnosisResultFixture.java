package com.sparta.learning.fixture;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;

import java.util.UUID;

// 규칙이 참조할 이전 진단 결과를 만듭니다 (규칙 코드만 사용하고 나머지는 기본값)
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
}
