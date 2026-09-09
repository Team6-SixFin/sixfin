package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.AiRequest;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.AiRequestRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
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

    private final AiClientPort aiClientPort;
    private final ObjectMapper objectMapper;
    private final FeedbackRepository feedbackRepository;
    private final AiRequestRepository aiRequestRepository;
    private final TransactionTemplate transactionTemplate;

    // [수정됨] 분리된 비동기 전담 메서드
    @Async("aiThreadPoolTaskExecutor")
    public CompletableFuture<AiFeedbackResponse> processAiFeedbackAsync(LearningCommandService.GenerationContext context) {
        if (context.isAlreadyProcessed()) {
            log.info("이미 처리 중이거나 완료된 피드백입니다. 중복 AI 호출을 방지합니다. Key: {}", context.feedback().getFeedbackKey());
            // 완료된 건이면 기존 데이터를 반환, 처리 중(PROCESSING)이면 null 반환
            if (context.feedback().getContent() != null) {
                try {
                    return CompletableFuture.completedFuture(objectMapper.treeToValue(context.feedback().getContent(), AiFeedbackResponse.class));
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
        AiFeedbackResponse aiResponse = null;
        String feedbackKey = context.feedback().getFeedbackKey();

        try {
            aiResponse = aiClientPort.requestAiFeedback(
                    context.feedback().getPositionId(),
                    context.feedback().getFeedbackType(),
                    context.contextJsonStr()
            );

            validateAiResponse(aiResponse);

            final AiFeedbackResponse finalAiResponse = aiResponse;
            transactionTemplate.executeWithoutResult(status ->
                    completeFeedback(feedbackKey, context.contextJsonStr(), finalAiResponse, requestId, modelName, promptVersion)
            );

        } catch (Exception e) {
            log.error("피드백 생성/파싱 실패", e);
            transactionTemplate.executeWithoutResult(status ->
                    failFeedback(feedbackKey, context.contextJsonStr(), e.getMessage(), requestId, modelName, promptVersion)
            );
            // 예외를 던져 TradeEventFacade의 exceptionally가 잡도록 함
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }

        return CompletableFuture.completedFuture(aiResponse);
    }


    private void validateAiResponse(AiFeedbackResponse response) {
        if (response.summary() == null || response.summary().isBlank() ||
                response.overview() == null || response.overview().isBlank() ||
                response.strengths() == null || response.strengths().isEmpty() ||
                response.improvements() == null || response.improvements().isEmpty() ||
                response.nextActions() == null || response.nextActions().isEmpty()) {
            throw new CustomException(LearningErrorCode.AI_RESPONSE_INCOMPLETE);
        }
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