package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 컨텍스트 축소 정책 값.
 *
 * 왜 프로퍼티인가:
 * 성능측정 7번(AI 컨텍스트 증가 테스트)에서 N(최근 체결 개수)을 10 / 20 / 50으로 바꿔가며 토큰 감소율과 피드백 품질을 비교해야 한다.
 * (현재 20 고정이며, 바꿔가며 하는 테스트 생략해도 무관.)
 * N이 코드에 하드코딩되면 그 비교 자체가 불가능하다.
 *
 * 개선 전 동작을 재현하는 스위치는 두지 않는다.
 * 베이스라인은 이 PR이 merge되기 전에서 측정하고, 개선 후 수치는 merge 후에 측정한다.
 * 죽은 분기를 운영 코드에 남기지 않기 위한 선택이다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.ai.context")
public class AiContextProperties {

    /**
     * AI 컨텍스트에 포함할 "최근 체결" 개수. 이 값을 넘는 구간부터 축소가 작동한다.
     * 진단 건수는 이 값과 무관하다. 진단은 규칙 × 판정결과로 묶여 최대 32그룹으로 자동 제한된다.
     */
    private int recentExecutions = 20;

    /** 대표 체결이 아닌 행의 investmentReason 최대 길이(문자). 0 이하면 절단하지 않는다. */
    private int investmentReasonMaxChars = 200;

    /** feedback_diagnoses에 연결할 진단 최대 건수. RuleCode 8종 × DiagnosisStatus 4종 = 32가 이론적 상한. */
    private int maxLinkedDiagnoses = 32;
}