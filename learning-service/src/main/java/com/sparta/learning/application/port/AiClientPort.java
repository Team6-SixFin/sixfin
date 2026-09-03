package com.sparta.learning.application.port;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;

public interface AiClientPort {
    // 프롬프트를 생성하고 AI를 호출한 뒤 파싱된 결과를 반환
    AiFeedbackResponse requestOnDemandFeedback(String positionId, String contextJson);
}