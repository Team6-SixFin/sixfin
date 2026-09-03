package com.sparta.learning.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
// @RequiredArgsConstructor
public class GeminiAiAdapter implements AiClientPort {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    // Builder를 주입받아 ChatClient를 .build()로 생성합니다. (1.0.0-M1 버전 이후 변경 사항 때문)
    public GeminiAiAdapter(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiFeedbackResponse requestOnDemandFeedback(String positionId, String contextJson) {
        // 1. 시스템 메시지 설정
        Message systemMessage = new SystemPromptTemplate(PromptTemplate.SYSTEM_PROMPT).createMessage();

        // 2. 유저 메시지(컨텍스트 주입)
        String userPrompt = PromptTemplate.ON_DEMAND_PROMPT
                .replace("{positionId}", positionId)
                .replace("{contextJson}", contextJson);
        Message userMessage = new UserMessage(userPrompt);

        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        try {
            // 정상적으로 chatClient.prompt() 호출 가능
            String responseJson = chatClient.prompt(prompt).call().content();
            log.info("Gemini AI Response: {}", responseJson);

            // 4. JSON 파싱
            return objectMapper.readValue(responseJson, AiFeedbackResponse.class);
        } catch (Exception e) {
            log.error("AI 호출 또는 JSON 파싱 실패", e);
            throw new RuntimeException("AI 피드백 생성 중 오류 발생", e);
        }
    }
}