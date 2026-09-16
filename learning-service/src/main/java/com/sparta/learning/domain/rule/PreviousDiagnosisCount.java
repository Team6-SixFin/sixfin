package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.model.DiagnosisStatus;

 /** 같은 포지션의 이전 진단을 규칙 코드와 판정 결과 조합별로 센 값 */
public record PreviousDiagnosisCount(
        String ruleCode,
        DiagnosisStatus result,
        long count
) {
}
