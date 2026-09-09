package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.content.FeedbackLearningResourceService;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.persistence.repository.AiRequestRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFeedbackProcessorTest {

    @Mock private AiClientPort aiClientPort;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private FeedbackLearningResourceService feedbackLearningResourceService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiFeedbackProcessor aiFeedbackProcessor;

    @BeforeEach
    void setUp() {
        aiFeedbackProcessor = new AiFeedbackProcessor(
                aiClientPort,
                objectMapper,
                feedbackRepository,
                aiRequestRepository,
                transactionTemplate,
                feedbackLearningResourceService
        );

        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("AI 피드백 저장이 완료되면 진단 기반 학습 자료를 연결한다")
    void recommendsLearningResourcesAfterFeedbackCompletion() {
        Feedback feedback = createProcessingFeedback();
        AiFeedbackResponse aiResponse = validAiResponse();
        when(aiClientPort.requestAiFeedback(
                feedback.getPositionId(),
                feedback.getFeedbackType(),
                "{}"
        )).thenReturn(aiResponse);
        when(feedbackRepository.findByFeedbackKey(feedback.getFeedbackKey()))
                .thenReturn(Optional.of(feedback));
        when(feedbackLearningResourceService.recommendAndLink(
                feedback.getFeedbackKey(),
                feedback.getUserId(),
                feedback.getPositionId(),
                feedback.getFeedbackType()
        )).thenReturn(2);

        AiFeedbackResponse result = aiFeedbackProcessor.processAiFeedbackAsync(
                new LearningCommandService.GenerationContext(feedback, "{}", false)
        ).join();

        assertEquals(aiResponse, result);
        assertEquals(FeedbackStatus.COMPLETED, feedback.getStatus());
        verify(feedbackLearningResourceService).recommendAndLink(
                feedback.getFeedbackKey(),
                feedback.getUserId(),
                feedback.getPositionId(),
                feedback.getFeedbackType()
        );
    }

    @Test
    @DisplayName("학습 자료 추천 실패는 완료된 AI 피드백 결과에 영향을 주지 않는다")
    void keepsAiFeedbackWhenLearningResourceRecommendationFails() {
        Feedback feedback = createProcessingFeedback();
        AiFeedbackResponse aiResponse = validAiResponse();
        when(aiClientPort.requestAiFeedback(
                feedback.getPositionId(),
                feedback.getFeedbackType(),
                "{}"
        )).thenReturn(aiResponse);
        when(feedbackRepository.findByFeedbackKey(feedback.getFeedbackKey()))
                .thenReturn(Optional.of(feedback));
        when(feedbackLearningResourceService.recommendAndLink(
                feedback.getFeedbackKey(),
                feedback.getUserId(),
                feedback.getPositionId(),
                feedback.getFeedbackType()
        )).thenThrow(new IllegalStateException("YouTube 검색 실패"));

        AiFeedbackResponse result = assertDoesNotThrow(() ->
                aiFeedbackProcessor.processAiFeedbackAsync(
                        new LearningCommandService.GenerationContext(feedback, "{}", false)
                ).join()
        );

        assertEquals(aiResponse, result);
        assertEquals(FeedbackStatus.COMPLETED, feedback.getStatus());
    }

    private Feedback createProcessingFeedback() {
        UUID positionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Feedback feedback = Feedback.builder()
                .feedbackKey("ON_DEMAND_FEEDBACK:" + positionId + ":" + executionId)
                .userId(UUID.randomUUID())
                .positionId(positionId)
                .feedbackType(FeedbackType.ON_DEMAND_FEEDBACK)
                .basedOnExecutionId(executionId)
                .build();
        feedback.updateStatus(FeedbackStatus.PROCESSING);
        return feedback;
    }

    private AiFeedbackResponse validAiResponse() {
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
