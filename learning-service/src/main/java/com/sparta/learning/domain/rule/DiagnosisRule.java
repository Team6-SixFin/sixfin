package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.RuleCode;

// 진단 규칙 인터페이스
// 체결 하나를 보는 규칙과 포지션 전체를 보는 규칙이 같은 시그니처를 쓰도록
// 판정에 필요한 재료를 DiagnosisContext로 묶어 받는다
public interface DiagnosisRule {

    // 이 규칙의 식별자
    RuleCode getRuleCode();
    // 이 체결에 어떤 규칙을 적용할지 판단 (Trade 단계에서 buy/sell 구분 용도)
    boolean supports(DiagnosisContext context);
    // 판정 결과 생성
    // 적용 대상이지만 판정에 필요한 값이 없으면 NOT_APPLICABLE로 이력을 남긴다
    DiagnosisResult diagnose(DiagnosisContext context);
}
