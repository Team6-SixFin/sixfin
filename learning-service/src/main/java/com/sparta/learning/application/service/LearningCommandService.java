package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto.*;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.*;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningCommandService {

    private final AiClientPort aiClientPort;
    private final ObjectMapper objectMapper;
    private final FeedbackRepository feedbackRepository;
    private final AiRequestRepository aiRequestRepository;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final DiagnosisResultRepository diagnosisResultRepository;
    private final ClosedPositionSnapshotRepository closedPositionSnapshotRepository;

    // Spring AOP 자기 호출(Self-Invocation) 이슈를 방지하고
    // 프로그래밍 방식으로 안전하게 트랜잭션을 관리하기 위한 템플릿
    private final TransactionTemplate transactionTemplate;

    /**
     * 1. 요청형 매매 피드백 생성 (사용자 API 호출)
     */
    public AiFeedbackResponse createOnDemandFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.ON_DEMAND_FEEDBACK)
        );
        return processAiFeedback(context);
    }

    /**
     * 2. 최초 매수 진입 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    public AiFeedbackResponse createEntryFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.ENTRY_FEEDBACK)
        );
        return processAiFeedback(context);
    }

    /**
     * 3. 포지션 종료 리뷰 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    public AiFeedbackResponse createPositionReviewFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.POSITION_REVIEW)
        );
        return processAiFeedback(context);
    }

    // =================================================================================
    // [트랜잭션 1] 데이터를 조회하고 PENDING 상태의 Feedback을 생성/반환 (DB 커넥션 점유 구간)
    // =================================================================================
    GenerationContext prepareGenerationContext(UUID positionId, UUID userId, FeedbackType feedbackType) {

        UUID basedOnExecutionId;
        String contextJsonStr;

        // 피드백 타입별 최적화된 쿼리 및 Context JSON 조립
        switch (feedbackType) {
            case ENTRY_FEEDBACK:
                ExecutionSnapshot firstExec = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_FIRST_TRADE_NOT_FOUND));
                basedOnExecutionId = firstExec.getExecutionId();
                contextJsonStr = buildEntryContextJson(firstExec, positionId, userId);
                break;

            case ON_DEMAND_FEEDBACK:
                ExecutionSnapshot latestExecForDemand = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));
                basedOnExecutionId = latestExecForDemand.getExecutionId();
                contextJsonStr = buildOnDemandContextJson(latestExecForDemand, positionId, userId);
                break;

            case POSITION_REVIEW:
            default:
                ExecutionSnapshot latestExecForReview = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));
                basedOnExecutionId = latestExecForReview.getExecutionId();
                contextJsonStr = buildReviewContextJson(latestExecForReview, positionId, userId);
                break;
        }

        // 멱등성 보장 검사 및 PENDING 엔티티 저장
        // 고유 feedbackKey 생성 (테이블 명세 규칙 : {feedback_type}:{position_id}:{based_on_execution_id})
        String feedbackKey = String.format("%s:%s:%s", feedbackType.name(), positionId, basedOnExecutionId);
        Optional<Feedback> existingFeedback = feedbackRepository.findByFeedbackKey(feedbackKey);

        Feedback feedback;
        boolean isAlreadyCompleted = false;

        if (existingFeedback.isPresent() && existingFeedback.get().getContent() != null) {
            feedback = existingFeedback.get();
            isAlreadyCompleted = true; // 이미 완료된 피드백 (AI 재호출 방지)
        } else {
            feedback = existingFeedback.orElseGet(() -> feedbackRepository.save(
                    Feedback.builder()
                            .feedbackKey(feedbackKey)
                            .userId(userId)
                            .positionId(positionId)
                            .basedOnExecutionId(basedOnExecutionId)
                            .feedbackType(feedbackType)
                            .build()
            ));
        }

        return new GenerationContext(feedback, contextJsonStr, isAlreadyCompleted);
    }

    // =================================================================================
    // [NO 트랜잭션] AI API를 호출하는 영역 (DB 커넥션 반납 후 외부 통신 진행)
    // =================================================================================
    private AiFeedbackResponse processAiFeedback(GenerationContext context) {
        if (context.isAlreadyCompleted()) {
            log.info("이미 존재하는 피드백입니다. 기존 데이터를 반환합니다. Key: {}", context.feedback().getFeedbackKey());
            try {
                return objectMapper.treeToValue(context.feedback().getContent(), AiFeedbackResponse.class);
            } catch (Exception e) {
                log.error("기존 피드백 Content 파싱 실패", e);
            }
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

            // [리뷰 반영] AI 응답 필수값 검증 로직 추가
            validateAiResponse(aiResponse);

            // [리뷰 반영] Detached Entity 이슈 방지를 위해 객체 대신 식별자(Key) 전달
            final AiFeedbackResponse finalAiResponse = aiResponse;
            transactionTemplate.executeWithoutResult(status ->
                    completeFeedback(feedbackKey, context.contextJsonStr(), finalAiResponse, requestId, modelName, promptVersion)
            );

        } catch (Exception e) {
            log.error("피드백 생성/파싱 실패", e);
            transactionTemplate.executeWithoutResult(status ->
                    failFeedback(feedbackKey, context.contextJsonStr(), e.getMessage(), requestId, modelName, promptVersion)
            );
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }

        return aiResponse;
    }

    // =================================================================================
    // [트랜잭션 2] 성공/실패 시 상태 업데이트 및 이력 저장 (빠르게 커넥션 점유 후 반납)
    // =================================================================================
    protected void completeFeedback(String feedbackKey, String contextJson, AiFeedbackResponse aiResponse, String reqId, String model, String version) {
        // [리뷰 반영] 영속성 컨텍스트(Managed) 상태로 가져오기
        Feedback managedFeedback = feedbackRepository.findByFeedbackKey(feedbackKey)
                .orElseThrow(() -> new IllegalStateException("피드백 데이터를 찾을 수 없습니다: " + feedbackKey));

        JsonNode contentNode = objectMapper.valueToTree(aiResponse);
        JsonNode inputNode = null;
        try {inputNode = objectMapper.readTree(contextJson);} catch (JsonProcessingException ignored) {}

        managedFeedback.complete(contentNode, true, version);
        aiRequestRepository.save(AiRequest.success(managedFeedback, reqId, model, version, inputNode, contentNode));
    }
    // TODO : AI 답변 생성 실패시 현재는 FAIL로 저장되나, 팀원과 상의 후 수정 해야 함 (fail과 fallback)
    protected void failFeedback(String feedbackKey, String contextJson, String errorMsg, String reqId, String model, String version) {
        Feedback managedFeedback = feedbackRepository.findByFeedbackKey(feedbackKey)
                .orElseThrow(() -> new IllegalStateException("피드백 데이터를 찾을 수 없습니다: " + feedbackKey));

        JsonNode inputNode = null;
        try { inputNode = objectMapper.readTree(contextJson); } catch (JsonProcessingException ignored) {}

        managedFeedback.fail(errorMsg);
        aiRequestRepository.save(AiRequest.failed(managedFeedback, reqId, model, version, inputNode, errorMsg));
    }

    // =================================================================================
    // AI Context JSON 빌더 헬퍼 메서드 (오버페칭 방지)
    // =================================================================================

    // 1. ENTRY (첫 체결과 ENTRY 진단만 조회)
    private String buildEntryContextJson(ExecutionSnapshot firstExec, UUID positionId, UUID userId) {
        List<DiagnosisResult> entryDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId).stream()
                .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY).toList();

        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                FeedbackType.ENTRY_FEEDBACK.name(), "v1.0", userId, positionId,
                new StockDto(firstExec.getStockId(), firstExec.getStockSymbol(), firstExec.getStockName()),
                new PositionDto("OPEN", firstExec.getPositionAveragePrice(), firstExec.getPositionQuantityAfter(), firstExec.getPlannedStopLossPrice()),
                null, null, // closedInfo, previousSummary 불필요
                List.of(mapToExecutionDto(firstExec)),
                mapToMarketContextDto(firstExec),
                entryDiagnoses.stream().map(this::mapToDiagnosisDto).toList()
        );
        return serializeToJson(requestDto);
    }

    // 2. ON_DEMAND (전체 체결과 ENTRY/TRADE 진단 조회)
    private String buildOnDemandContextJson(ExecutionSnapshot latestExec, UUID positionId, UUID userId) {
        List<ExecutionSnapshot> allExecutions = executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId);
        List<DiagnosisResult> diagnoses = diagnosisResultRepository.findAllByPositionId(positionId).stream()
                .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY || d.getDiagnosisPhase() == DiagnosisPhase.TRADE).toList();

        // [리뷰 반영] 이전 피드백 요약본 가져오기 (가장 최근 피드백의 summary 파싱)
        String previousSummary = null;
        Optional<Feedback> prevFeedbackOpt = feedbackRepository.findByFeedbackKey(
                String.format("%s:%s:%s", FeedbackType.ENTRY_FEEDBACK.name(), positionId, allExecutions.get(0).getExecutionId())
        ); // 가장 간단한 방법으로 최초 ENTRY 피드백 참조 (추후 고도화 가능)

        if (prevFeedbackOpt.isPresent() && prevFeedbackOpt.get().getContent() != null) {
            try {
                previousSummary = objectMapper.treeToValue(prevFeedbackOpt.get().getContent(), AiFeedbackResponse.class).summary();
            } catch (Exception ignored) {}
        }

        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                FeedbackType.ON_DEMAND_FEEDBACK.name(), "v1.0", userId, positionId,
                new StockDto(latestExec.getStockId(), latestExec.getStockSymbol(), latestExec.getStockName()),
                new PositionDto("OPEN", latestExec.getPositionAveragePrice(), latestExec.getPositionQuantityAfter(), latestExec.getPlannedStopLossPrice()),
                null, previousSummary,
                allExecutions.stream().map(this::mapToExecutionDto).toList(),
                mapToMarketContextDto(latestExec),
                diagnoses.stream().map(this::mapToDiagnosisDto).toList()
        );
        return serializeToJson(requestDto);
    }

    // 3. POSITION_REVIEW (종료 정보 조회 및 전체 체결/진단)
    private String buildReviewContextJson(ExecutionSnapshot latestExec, UUID positionId, UUID userId) {
        ClosedPositionSnapshot closedPos = closedPositionSnapshotRepository.findByPositionId(positionId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.CLOSED_POSITION_NOT_FOUND));

        List<ExecutionSnapshot> allExecutions = executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId);
        List<DiagnosisResult> allDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId);

        // [리뷰 반영] ClosedInfoDto 생성하여 정확한 시스템 손익/수량 전달
        ClosedInfoDto closedInfoDto = new ClosedInfoDto(
                closedPos.getAverageExitPrice(),
                closedPos.getTotalBoughtQuantity(),
                closedPos.getTotalSoldQuantity(),
                closedPos.getRealizedProfit(),
                closedPos.getRealizedReturnRate(),
                closedPos.getOpenedAt() != null ? closedPos.getOpenedAt().toString() : null,
                closedPos.getClosedAt() != null ? closedPos.getClosedAt().toString() : null
        );

        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                FeedbackType.POSITION_REVIEW.name(), "v1.0", userId, positionId,
                new StockDto(closedPos.getStockId(), closedPos.getStockSymbol(), closedPos.getStockName()),
                new PositionDto("CLOSED", closedPos.getAverageEntryPrice(), 0, closedPos.getPlannedStopLossPrice()),
                closedInfoDto, null, // closedInfo 포함
                allExecutions.stream().map(this::mapToExecutionDto).toList(),
                mapToMarketContextDto(latestExec),
                allDiagnoses.stream().map(this::mapToDiagnosisDto).toList()
        );
        return serializeToJson(requestDto);
    }

    // --- 변환용 하위 Helper 메서드 ---
    private String serializeToJson(AiFeedbackRequestDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("AI 요청 JSON 직렬화 실패: positionId={}", dto.positionId(), e);
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }
    }

    private ExecutionDto mapToExecutionDto(ExecutionSnapshot exec) {
        return new ExecutionDto(exec.getExecutionId(), exec.getTradeType().name(), exec.getQuantity(), exec.getExecutedPrice(), exec.getPositionQuantityAfter(), exec.getInvestmentReason(), exec.getExecutedAt().toString());
    }

    private MarketContextDto mapToMarketContextDto(ExecutionSnapshot exec) {
        return new MarketContextDto(exec.getRecent20dHigh(), exec.getRecent20dLow(), exec.getRecent5dReturnRate(), exec.getQuoteAt().toString());
    }

    private DiagnosisDto mapToDiagnosisDto(DiagnosisResult diag) {
        return new DiagnosisDto(diag.getRuleCode(), diag.getRuleVersion(), diag.getResult().name(), diag.getMetricValue(), diag.getThresholdValue(), diag.getMetrics(), diag.getEvidence());
    }

    // [리뷰반영]: Ai 응답 필수 부분 검증
    private void validateAiResponse(AiFeedbackResponse response) {
        if (response.summary() == null || response.summary().isBlank() ||
                response.overview() == null || response.overview().isBlank() ||
                response.strengths() == null || response.strengths().isEmpty() ||
                response.improvements() == null || response.improvements().isEmpty() ||
                response.nextActions() == null || response.nextActions().isEmpty()) {
            throw new CustomException(LearningErrorCode.AI_RESPONSE_INCOMPLETE);
        }
    }


    // 내부 메서드간 통신용 Record
    record GenerationContext(Feedback feedback, String contextJsonStr, boolean isAlreadyCompleted) {}
}