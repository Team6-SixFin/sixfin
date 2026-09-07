package com.sparta.learning.application.dto.response;

import java.util.List;

// AI가 응답하는 JSON 포맷을 매핑할 레코드
public record AiFeedbackResponse(
        String summary,                   // 최상위 한 문장 요약 (목록 API용)
        String overview,                 // 전체 상황 설명
        List<String> strengths,          // 잘한 점 배열
        List<String> improvements,       // 개선할 점 배열
        List<String> nextActions,        // 다음 행동 제안 배열
        List<String> reflectionQuestions // 회고 질문 (선택적)
) {}