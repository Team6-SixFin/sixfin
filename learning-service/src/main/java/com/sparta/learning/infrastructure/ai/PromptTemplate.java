package com.sparta.learning.infrastructure.ai;

// TODO : 추후 프롬프트 고도화 필요, 포지션 ID 넘길 필요 없을 것 같아 상의 후 수정
// AI 프롬프트 관리
public class PromptTemplate {

    // 시스템 프롬프트: JSON 강제
    public static final String SYSTEM_PROMPT =
            """
            너는 주식 트레이딩 전문가이자 멘토야. 사용자의 매매 기록과 규칙 기반 진단 결과를 바탕으로 객관적이고 유용한 피드백을 제공해. \
            반드시 아래의 순수 JSON 형식으로만 응답해. 마크다운이나 추가 설명은 절대 포함하지 마.
            {
              "summary": "최상위 한 문장 요약 (목록 API용)",
              "overview": "전체 상황 설명 및 총평",
              "strengths": ["잘한 점1", "잘한 점2"],
              "improvements": ["개선할 점1"],
              "nextActions": ["다음 행동 제안1"],
              "reflectionQuestions": ["회고 질문1"]
            }""";

    // 1. 요청형 매매 피드백 (중간 점검)
    public static final String ON_DEMAND_PROMPT =
            """
            사용자가 현재 보유 중인 포지션에 대해 중간 점검(ON_DEMAND) 피드백을 요청했습니다. \
            진입 이후의 추가 매수/매도 내역(executions)과 현재 포지션 상태를 분석하여, \
            초기 투자 원칙을 잘 지키고 있는지 점검하고 향후 시장 변동성에 대비한 대응 전략(nextActions)을 제시해 주세요.
            
            분석 대상 데이터:
            {contextJson}""";

    // 2. 최초 매수 진입 피드백
    public static final String ENTRY_PROMPT =
            """
            사용자가 신규 진입(ENTRY)을 완료했습니다. 전달된 JSON 데이터의 'marketContext'와 첫 'executions' 데이터를 분석하여 \
            진입 시점의 시장 상황(20일 고점/저점 대비 위치 등)이 적절했는지, 투자 이유(investmentReason)가 논리적인지, \
            계획된 손절가(plannedStopLossPrice)가 리스크 관리 차원에서 적합한지 평가해 주세요.
            
            분석 대상 데이터:
            {contextJson}""";

    // 3. 포지션 종료 리뷰 피드백
    public static final String POSITION_REVIEW_PROMPT =
            """
            규칙 기반 진단 결과(diagnoses)를 종합적으로 분석해 주세요. \
            수익/손실 원인을 객관적으로 분석하고, 다음 트레이딩에 적용할 수 있는 구체적인 개선점(improvements)과 \
            스스로 돌아볼 수 있는 회고 질문(reflectionQuestions)을 포함해 주세요.
            
            분석 대상 데이터:
            {contextJson}""";
}