package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.model.FeedbackType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

// properties 속성을 추가하여 테스트 환경에서 사용할 값을 직접 넣어줍니다.
/*
 * 테스트를 위한 설정
 * 1. application.yml 의 base-url: http://localhost:12345/v1/ 변경(일부러 열려있지 않은 포트로 변경)
 * 2. AiClientConfig.java 의 factory.setReadTimeout(30) -> factory.setReadTimeout(1) 변경 (테스트를 위한 1ms 설정)
 * 3. @SpringBootTest(properties{...})에 값 설정
 * */
@SpringBootTest(properties = {
        "POSTGRES_USER=test",                // 본인의 로컬 DB 아이디로 변경하세요
        "POSTGRES_PASSWORD=test",            // 본인의 로컬 DB 비밀번호로 변경하세요
        "AI_API_KEY=insert.AI_API_KEY"      // AI API키
})
class GeminiAiAdapterIntegrationTest {

    @Autowired
    private AiClientPort aiClientPort;

    @Test
    @DisplayName("1ms 타임아웃 시 Gemini API 호출이 실패하고 재시도(Retry)가 동작한다")
    void testAiTimeoutAndRetry() {
        // given: 가짜 식별자와 더미 JSON 컨텍스트 준비
        UUID dummyPositionId = UUID.randomUUID();
        String dummyContextJson = "{\"test\": \"타임아웃 테스트용 더미 데이터입니다.\"}";

        System.out.println("==========================================================");
        System.out.println("AI 피드백 요청 시작! (1ms 타임아웃 및 재시도 대기 시간 관찰)");
        System.out.println("==========================================================");

        long startTime = System.currentTimeMillis();

        // when & then
        try {
            // 실제 스프링 빈(AiClientConfig 설정 포함)을 타고 외부 API를 찌릅니다.
            aiClientPort.requestAiFeedback(dummyPositionId, FeedbackType.ENTRY_FEEDBACK, dummyContextJson);
            System.out.println("실패: 타임아웃이 정상적으로 작동하지 않았습니다.");
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;

            System.out.println("==========================================================");
            System.out.println("타임아웃 및 재시도 로직 최종 실패 처리 완료!");
            System.out.println("총 소요 시간: 약 " + duration + "초");
            System.out.println("발생한 에러: " + e.getMessage());
            System.out.println("==========================================================");
        }
    }
}