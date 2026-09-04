package com.sparta.learning.infrastructure.ai;

// TODO : 추후 프롬프트 고도화 필요, 포지션 ID 넘길 필요 없을 것 같아 상의 후 수정
// AI 프롬프트 관리
public class PromptTemplate {

    // 시스템 프롬프트: JSON 강제
    public static final String SYSTEM_PROMPT =
            "너는 주식 트레이딩 전문가이자 멘토야. 사용자의 매매 기록과 규칙 기반 진단 결과를 바탕으로 객관적이고 유용한 피드백을 제공해. " +
            "반드시 아래의 순수 JSON 형식으로만 응답해. 마크다운이나 추가 설명은 절대 포함하지 마.\n" +
            "{\n  \"title\": \"피드백 요약 제목\",\n  \"content\": \"상세 피드백 내용\",\n  \"actionable_advice\": [\"실천 조언1\", \"실천 조언2\"]\n}";

    // 1. 요청형 매매 피드백 (중간 점검)
    public static final String ON_DEMAND_PROMPT =
            "사용자가 중간 점검 피드백을 요청했어. 현재 포지션 상태를 분석해 줘.\n" +
            "포지션 ID: {positionId}\n" +
            "현재까지의 매매 및 진단 요약 JSON: \n{contextJson}\n" +
            "리스크를 줄이기 위해 지금 시점에서 고려해야 할 전략을 제시해 줘.";

    // 2. 최초 매수 진입 피드백
    public static final String ENTRY_PROMPT =
            "사용자가 새로운 포지션에 진입(최초 매수)했어.\n" +
            "포지션 ID: {positionId}\n" +
            "진단 결과 요약 JSON: \n{contextJson}\n" +
            "매수 타점의 적절성(뇌동/추격매수 여부)과 계획된 손절가 설정 여부에 중점을 두고 피드백을 작성해 줘.";

    // 3. 포지션 종료 리뷰 피드백
    public static final String POSITION_REVIEW_PROMPT =
            "사용자가 포지션을 최종 종료했어.\n" +
            "포지션 ID: {positionId}\n" +
            "종료 스냅샷 및 전체 진단 요약 JSON: \n{contextJson}\n" +
            "최종 실현 수익률과 원칙 준수 여부를 평가하고, 이 트레이딩에서 잘한 점과 향후 개선점을 종합적으로 리뷰해 줘.";
}