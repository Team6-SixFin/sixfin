package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiAiAdapterTest {

    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec responseSpec;

    private GeminiAiAdapter adapter;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        adapter = new GeminiAiAdapter(chatClientBuilder);
    }

    @Test
    @DisplayName("Gemini 응답을 AiFeedbackResponse 구조로 변환한다")
    void convertsGeminiResponseToStructuredFeedback() {
        AiFeedbackResponse expected = validResponse();
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(AiFeedbackResponse.class)).thenReturn(expected);

        AiFeedbackResponse actual = adapter.requestAiFeedback(
                UUID.randomUUID(),
                FeedbackType.ON_DEMAND_FEEDBACK,
                "{}"
        );

        assertEquals(expected, actual);
        verify(responseSpec).entity(AiFeedbackResponse.class);
    }

    @Test
    @DisplayName("AI 호출 또는 구조화 변환 실패의 원인을 CustomException에 보존한다")
    void preservesStructuredOutputFailureCause() {
        IllegalArgumentException rootCause = new IllegalArgumentException("invalid response JSON");
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(AiFeedbackResponse.class)).thenThrow(rootCause);

        CustomException exception = assertThrows(CustomException.class, () ->
                adapter.requestAiFeedback(
                        UUID.randomUUID(),
                        FeedbackType.ON_DEMAND_FEEDBACK,
                        "{}"
                )
        );

        assertSame(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED, exception.getErrorCode());
        assertSame(rootCause, exception.getCause());
    }

    private AiFeedbackResponse validResponse() {
        return new AiFeedbackResponse(
                "요약",
                "총평",
                List.of("잘한 점"),
                List.of("개선할 점"),
                List.of("다음 행동"),
                List.of("회고 질문")
        );
    }
}
