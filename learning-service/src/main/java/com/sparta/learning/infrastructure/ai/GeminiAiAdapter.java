package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

// AiClientPort ai 호출 처리 구현체
@Slf4j
@Component
// @RequiredArgsConstructor
public class GeminiAiAdapter implements AiClientPort {

    private final ChatClient chatClient;

    // Builder를 주입받아 ChatClient를 .build()로 생성합니다. (1.0.0-M1 버전 이후 변경 사항 때문)
    public GeminiAiAdapter(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // 분기 처리를 위해 FeedbackType을 파라미터로 추가
    @Override
    public AiFeedbackResponse requestAiFeedback(UUID positionId, FeedbackType type, String contextJson) {

        // 1. 시스템 메시지 설정
        Message systemMessage = new SystemMessage(PromptTemplate.SYSTEM_PROMPT);

        // 피드백 타입별 프롬프트 분기 처리
        Message userMessage = getMessage(positionId, type, contextJson);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
        // 사용자 매매 정보가 운영 로그에 노출되지 않도록 요청 본문 대신 식별 정보만 남깁니다.
        log.debug("Gemini AI 요청 준비. positionId={}, feedbackType={}", positionId, type);

        try {
            // Spring AI의 구조화 출력 변환을 사용해 응답 형식과 DTO 변환을 한 곳에서 처리합니다.
            return chatClient.prompt(prompt)
                    .call()
                    .entity(AiFeedbackResponse.class);
        } catch (Exception e) {
            log.error("AI 호출 또는 구조화 응답 변환 실패. positionId={}, feedbackType={}", positionId, type, e);
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED, e);
        }
    }

    // 피드백 타입별 프롬프트 분기 처리 메서드
    private static @NonNull Message getMessage(UUID positionId, FeedbackType type, String contextJson) {
        String promptString;
        switch (type) {
            case ENTRY_FEEDBACK -> promptString = PromptTemplate.ENTRY_PROMPT;
            case ON_DEMAND_FEEDBACK -> promptString = PromptTemplate.ON_DEMAND_PROMPT;
            case POSITION_REVIEW -> promptString = PromptTemplate.POSITION_REVIEW_PROMPT;
            default -> throw new CustomException(LearningErrorCode.INVALID_FEEDBACK_TYPE);
        }

        // 2. 유저 메시지(컨텍스트 주입)
        String userPrompt = promptString
                .replace("{positionId}", positionId.toString())
                .replace("{contextJson}", contextJson);
        Message userMessage = new UserMessage(userPrompt);
        return userMessage;
    }
}
