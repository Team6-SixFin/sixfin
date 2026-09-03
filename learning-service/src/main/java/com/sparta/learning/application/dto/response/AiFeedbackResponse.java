package com.sparta.learning.application.dto.response;

import java.util.List;

// AI가 응답하는 JSON 포맷을 매핑할 레코드
public record AiFeedbackResponse(
        String title,
        String content,
        List<String> actionable_advice
) {}