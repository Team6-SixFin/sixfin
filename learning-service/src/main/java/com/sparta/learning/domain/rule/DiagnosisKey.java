package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.RuleCode;

import java.util.UUID;

// diagnosis_results.diagnosis_key 생성 규칙입니다
// {Phase}:{대상ID}:{ruleCode}:{version}
public final class DiagnosisKey {

    private static final String DELIMITER = ":";
    private static final String VERSION_PREFIX = "v";

    private DiagnosisKey() {}

    public static String of(RuleCode ruleCode, int ruleVersion, UUID executionId){
        return join(ruleCode.getDiagnosisPhase(), executionId, ruleCode, ruleVersion);
    }

    public static String ofPosition(RuleCode ruleCode, int ruleVersion, UUID positionId){
        return join(DiagnosisPhase.CLOSE, positionId, ruleCode, ruleVersion);
    }

    private static String join(DiagnosisPhase phase, UUID targetId, RuleCode ruleCode, int ruleVersion) {
        return phase.name()
                + DELIMITER + targetId
                + DELIMITER + ruleCode.name()
                + DELIMITER + VERSION_PREFIX + ruleVersion;
    }
}
