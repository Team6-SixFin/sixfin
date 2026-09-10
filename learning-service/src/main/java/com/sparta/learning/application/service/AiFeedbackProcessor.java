package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.content.FeedbackLearningResourceService;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.AiRequest;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.AiRequestRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiFeedbackProcessor {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String ALREADY_PROCESSED = "ALREADY_PROCESSED";

    private final AiClientPort aiClientPort;
    private final ObjectMapper objectMapper;
    private final FeedbackRepository feedbackRepository;
    private final AiRequestRepository aiRequestRepository;
    private final TransactionTemplate transactionTemplate;
    private final FeedbackLearningResourceService feedbackLearningResourceService;
    private final LearningMetrics learningMetrics;

    // [수정됨] 분리된 비동기 전담 메서드
    @Async("aiThreadPoolTaskExecutor")
    public CompletableFuture<AiFeedbackResponse> processAiFeedbackAsync(LearningCommandService.GenerationContext context) {
        String generationResult = FAILED;

        try {
            if (context.isAlreadyProcessed()) {
                generationResult = ALREADY_PROCESSED;
                log.info("이미 처리 중이거나 완료된 피드백입니다. 중복 AI 호출을 방지합니다. Key: {}", context.feedback().getFeedbackKey());
                // 완료된 건이면 기존 데이터를 반환, 처리 중(PROCESSING)이면 null 반환
                if (context.feedback().getContent() != null) {
                    try {
                        AiFeedbackResponse existingResponse =
                                objectMapper.treeToValue(context.feedback().getContent(), AiFeedbackResponse.class);
                        recommendLearningResourcesSafely(context.feedback());
                        return CompletableFuture.completedFuture(existingResponse);
                    } catch (Exception e) {
                        log.error("기존 피드백 Content 파싱 실패", e);
                    }
                }
                return CompletableFuture.completedFuture(null);
            }

            // TODO : 현재 사용 버전 하드코딩이라 추후 수정 해야 함
            // 공통 메타 정보 설정 (모델명 및 프롬프트 버전)
            String requestId = UUID.randomUUID().toString();
            String modelName = "gemini-3.5-flash";
            String promptVersion = "v1.0";
            AiFeedbackResponse aiResponse;
            String feedbackKey = context.feedback().getFeedbackKey();

            try {
                aiResponse = requestAiFeedback(context);

                final AiFeedbackResponse finalAiResponse = aiResponse;
                transactionTemplate.executeWithoutResult(status ->
                        completeFeedback(feedbackKey, context.contextJsonStr(), finalAiResponse, requestId, modelName, promptVersion)
                );
                recommendLearningResourcesSafely(context.feedback());
                generationResult = SUCCESS;

            } catch (Exception e) {
                log.error("피드백 생성/파싱 실패", e);
                String failureReason = describeFailure(e);
                transactionTemplate.executeWithoutResult(status ->
                        failFeedback(feedbackKey, context.contextJsonStr(), failureReason, requestId, modelName, promptVersion)
                );
                // 이미 분류된 도메인 오류는 유지하고, 예상하지 못한 오류만 공통 AI 오류로 변환합니다.
                if (e instanceof CustomException customException) {
                    throw customException;
                }
                throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED, e);
            }

            return CompletableFuture.completedFuture(aiResponse);
        } finally {
            learningMetrics.recordFeedbackGeneration(
                    context.feedback().getFeedbackType(),
                    generationResult,
                    context.generationStartedAtNanos()
            );
        }
    }

    /** AI 외부 호출과 구조화 응답 검증 구간만 따로 측정한다. */
    private AiFeedbackResponse requestAiFeedback(LearningCommandService.GenerationContext context) {
        Timer.Sample sample = learningMetrics.startTimer();
        learningMetrics.recordAiContextSize(
                context.feedback().getFeedbackType(),
                context.contextJsonStr()
        );

        try {
            AiFeedbackResponse response = aiClientPort.requestAiFeedback(
                    context.feedback().getPositionId(),
                    context.feedback().getFeedbackType(),
                    context.contextJsonStr()
            );
            validateAiResponse(response);
            learningMetrics.recordAiRequest(context.feedback().getFeedbackType(), SUCCESS, sample);
            return response;
        } catch (RuntimeException exception) {
            learningMetrics.recordAiRequest(context.feedback().getFeedbackType(), FAILED, sample);
            throw exception;
        }
    }

    /** 학습 자료 추천 실패가 이미 완료된 AI 피드백의 성공 상태와 응답에 영향을 주지 않게 격리합니다. */
    private void recommendLearningResourcesSafely(Feedback feedback) {
        Timer.Sample sample = learningMetrics.startTimer();

        try {
            int linkedCount = feedbackLearningResourceService.recommendAndLink(
                    feedback.getFeedbackKey(),
                    feedback.getUserId(),
                    feedback.getPositionId(),
                    feedback.getFeedbackType()
            );
            log.info(
                    "피드백 학습 자료 연결 완료. feedbackKey={}, linkedCount={}",
                    feedback.getFeedbackKey(),
                    linkedCount
            );
            learningMetrics.recordLearningResourceRecommendation(feedback.getFeedbackType(), SUCCESS, sample);
        } catch (RuntimeException exception) {
            learningMetrics.recordLearningResourceRecommendation(feedback.getFeedbackType(), FAILED, sample);
            log.warn(
                    "피드백 학습 자료 추천 실패, AI 피드백은 유지합니다. feedbackKey={}",
                    feedback.getFeedbackKey(),
                    exception
            );
        }
    }


    private void validateAiResponse(AiFeedbackResponse response) {
        if (response == null ||
                response.summary() == null || response.summary().isBlank() ||
                response.overview() == null || response.overview().isBlank() ||
                response.strengths() == null || response.strengths().isEmpty() ||
                response.improvements() == null || response.improvements().isEmpty() ||
                response.nextActions() == null || response.nextActions().isEmpty()) {
            throw new CustomException(LearningErrorCode.AI_RESPONSE_INCOMPLETE);
        }
    }

    /** DB 실패 이력에는 공통 메시지가 아닌 가장 안쪽 예외 유형과 메시지를 남깁니다. */
    private String describeFailure(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private void completeFeedback(String feedbackKey, String contextJson, AiFeedbackResponse aiResponse, String reqId, String model, String version) {
        // 영속성 컨텍스트(Managed) 상태로 가져오기
        Feedback managedFeedback = feedbackRepository.findByFeedbackKey(feedbackKey)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FEEDBACK_NOT_FOUND));

        JsonNode contentNode = objectMapper.valueToTree(aiResponse);
        JsonNode inputNode = null;
        try {inputNode = objectMapper.readTree(contextJson);} catch (JsonProcessingException ignored) {}

        managedFeedback.complete(contentNode, true, version);
        aiRequestRepository.save(AiRequest.success(managedFeedback, reqId, model, version, inputNode, contentNode));
    }

    // TODO : AI 답변 생성 실패시 현재는 FAIL로 저장되나, 팀원과 상의 후 수정 해야 함 (fail과 fallback)
    private void failFeedback(String feedbackKey, String contextJson, String errorMsg, String reqId, String model, String version) {
        Feedback managedFeedback = feedbackRepository.findByFeedbackKey(feedbackKey)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FEEDBACK_NOT_FOUND));

        JsonNode inputNode = null;
        try { inputNode = objectMapper.readTree(contextJson); } catch (JsonProcessingException ignored) {}

        managedFeedback.fail(errorMsg);
        aiRequestRepository.save(AiRequest.failed(managedFeedback, reqId, model, version, inputNode, errorMsg));
    }
}
