package com.sparta.learning.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RuleCode {
    // ENTRY
    STOP_LOSS_SET(DiagnosisPhase.ENTRY),    //매수 시 계획 손절가를 설정했는지
    STOP_LOSS_WIDTH(DiagnosisPhase.ENTRY),  // 매수가 대비 손절 폭이 적절한지
    HIGH_CHASING_BUY(DiagnosisPhase.ENTRY), // 최근 20일 최고가 부근에서 매수했는지
    SHORT_TERM_SURGE_BUY(DiagnosisPhase.ENTRY), // 최근 단기 상승 이후에 매수했는지
    // TRADE
    REPEATED_HIGH_CHASING_BUY(DiagnosisPhase.TRADE),    //추가 매수에서도 고점 추격이 반복됐는지
    SELL_BELOW_STOP_LOSS(DiagnosisPhase.TRADE), // 계획 손절가보다 낮은 가격에 매도했는지
    // CLOSE
    STOP_LOSS_ADHERENCE(DiagnosisPhase.CLOSE),  // 전체 매도 과정에서 손절 원칙을 지켰는지
    HIGH_CHASING_FREQUENCY(DiagnosisPhase.CLOSE);   // 고점 추격 매수가 반복됐는지

    private final DiagnosisPhase diagnosisPhase;

}