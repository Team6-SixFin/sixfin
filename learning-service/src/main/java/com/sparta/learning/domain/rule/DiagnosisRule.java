package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.RuleCode;

// 체결 스냅샷을 판정하는 진단 규칙 인터페이스
public interface DiagnosisRule {

    // 이 규칙의 식별자
    RuleCode getRuleCode();
    // 이 체결에 어떤 규칙을 적용할지 판단 (Trade 단계에서 buy/sell 구분 용도)
    boolean supports(ExecutionSnapshot snapshot);
    // 판정 결과 생성
    DiagnosisResult diagnose(ExecutionSnapshot snapshot);
}
