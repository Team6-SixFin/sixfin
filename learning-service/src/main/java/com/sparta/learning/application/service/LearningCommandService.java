package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto.*;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.entity.*;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.FeedbackStatus;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningCommandService {

    private final ObjectMapper objectMapper;
    private final FeedbackRepository feedbackRepository;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final DiagnosisResultRepository diagnosisResultRepository;
    private final ClosedPositionSnapshotRepository closedPositionSnapshotRepository;
    private final FeedbackDiagnosisRepository feedbackDiagnosisRepository;

    // Spring AOP 자기 호출(Self-Invocation) 이슈를 방지하고
    // 프로그래밍 방식으로 안전하게 트랜잭션을 관리하기 위한 템플릿
    private final TransactionTemplate transactionTemplate;

    // 분리된 비동기 AI 처리 Bean 주입
    private final AiFeedbackProcessor aiFeedbackProcessor;

    /**
     * 1. 요청형 매매 피드백 생성 (사용자 API 호출)
     */
    public AiFeedbackResponse createOnDemandFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.ON_DEMAND_FEEDBACK)
        );
        if (isProcessing(context)) {
            throw new CustomException(LearningErrorCode.FEEDBACK_GENERATION_IN_PROGRESS);
        }

        // 사용자 API 요청은 결과를 기다리되 비동기 예외의 원인을 복원해 기존 오류 응답을 유지한다.
        return awaitFeedback(aiFeedbackProcessor.processAiFeedbackAsync(context));
    }

    /**
     * 2. 최초 매수 진입 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    // [수정됨] @Async 제거 -> DB 저장은 동기로 수행하여 유실 방지
    public CompletableFuture<AiFeedbackResponse> createEntryFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.ENTRY_FEEDBACK)
        );
        // AI 호출만 비동기로 위임
        return aiFeedbackProcessor.processAiFeedbackAsync(context);
    }

    /**
     * 3. 포지션 종료 리뷰 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    // [수정됨] @Async 제거
    public CompletableFuture<AiFeedbackResponse> createPositionReviewFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.POSITION_REVIEW)
        );
        return aiFeedbackProcessor.processAiFeedbackAsync(context);
    }

    // =================================================================================
    // [트랜잭션 1] 데이터를 조회하고 PENDING/PROCESSING 상태의 Feedback을 생성/반환
    // =================================================================================
    GenerationContext prepareGenerationContext(UUID positionId, UUID userId, FeedbackType feedbackType) {

        UUID basedOnExecutionId;
        String contextJsonStr;
        // 피드백에 사용된 진단 결과를 추적하고 매핑하기 위한 리스트
        List<DiagnosisResult> usedDiagnoses;

        // 피드백 타입별 최적화된 쿼리 및 Context JSON 조립
        switch (feedbackType) {
            case ENTRY_FEEDBACK:
                ExecutionSnapshot firstExec = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_FIRST_TRADE_NOT_FOUND));
                basedOnExecutionId = firstExec.getExecutionId();

                // 헬퍼 메서드 내부에 있던 진단 결과 조회를 밖으로 분리
                usedDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId).stream()
                        .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY).toList();
                contextJsonStr = buildEntryContextJson(firstExec, positionId, userId, usedDiagnoses);
                break;

            case ON_DEMAND_FEEDBACK:
                ExecutionSnapshot latestExecForDemand = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));
                basedOnExecutionId = latestExecForDemand.getExecutionId();

                // 진단 결과 조회를 밖으로 분리
                usedDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId).stream()
                        .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY || d.getDiagnosisPhase() == DiagnosisPhase.TRADE).toList();
                contextJsonStr = buildOnDemandContextJson(latestExecForDemand, positionId, userId, usedDiagnoses);
                break;

            case POSITION_REVIEW:
            default:
                ExecutionSnapshot latestExecForReview = executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));
                basedOnExecutionId = latestExecForReview.getExecutionId();

                // 진단 결과 조회를 밖으로 분리
                usedDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId);
                contextJsonStr = buildReviewContextJson(latestExecForReview, positionId, userId, usedDiagnoses);
                break;
        }

        // 멱등성 보장 검사 및 PENDING 엔티티 저장
        // 고유 feedbackKey 생성 (테이블 명세 규칙 : {feedback_type}:{position_id}:{based_on_execution_id})
        String feedbackKey = String.format("%s:%s:%s", feedbackType.name(), positionId, basedOnExecutionId);
        Optional<Feedback> existingFeedback = feedbackRepository.findByFeedbackKey(feedbackKey);

        Feedback feedback;
        boolean isAlreadyProcessed = false;

        if (existingFeedback.isPresent()) {
            feedback = existingFeedback.get();
            // 완료되었거나(Content 존재), PROCESSING 상태이면 중복 호출 차단
            if (feedback.getContent() != null || feedback.getStatus() == FeedbackStatus.PROCESSING) {
                isAlreadyProcessed = true;
            } else {
                // PENDING(또는 FAILED) 상태인 경우 PROCESSING으로 갱신하여 점유
                feedback.updateStatus(FeedbackStatus.PROCESSING);
            }
        } else {
            feedback = Feedback.builder()
                    .feedbackKey(feedbackKey)
                    .userId(userId)
                    .positionId(positionId)
                    .basedOnExecutionId(basedOnExecutionId)
                    .feedbackType(feedbackType)
                    .build();

            feedback.updateStatus(FeedbackStatus.PROCESSING);
            feedback = feedbackRepository.save(feedback);
        }

        // [수정됨] if-else 블록 밖으로 꺼내어 기존/신규 상관없이 동작하도록 함
        // 이미 처리가 끝난(또는 진행 중인) 피드백이 아닐 때만 매핑 로직 수행
        if (!usedDiagnoses.isEmpty() && !isAlreadyProcessed) {

            // 1. 이미 이 피드백에 매핑되어 DB에 저장된 진단 결과들을 가져옴
            List<FeedbackDiagnosis> existingMappings = feedbackDiagnosisRepository.findAllByFeedbackId(feedback.getId());

            // 2. 이미 매핑된 DiagnosisResult 의 ID만 추출하여 Set 으로 만듦 (비교 속도 향상)
            java.util.Set<Long> existingDiagnosisIds = existingMappings.stream()
                    .map(mapping -> mapping.getDiagnosisResult().getId())
                    .collect(java.util.stream.Collectors.toSet());

            Feedback finalFeedback = feedback;

            // 3. 현재 쿼리해온 usedDiagnoses 중에서 '아직 DB에 매핑되지 않은(새로운) 진단 결과'만 필터링
            List<FeedbackDiagnosis> newMappings = usedDiagnoses.stream()
                    .filter(diag -> !existingDiagnosisIds.contains(diag.getId())) // 중복 걸러내기!
                    .map(diag -> FeedbackDiagnosis.builder()
                            .feedback(finalFeedback)
                            .diagnosisResult(diag)
                            .build())
                    .toList();

            // 4. 새로운 매핑이 있을 때만 saveAll 실행
            if (!newMappings.isEmpty()) {
                feedbackDiagnosisRepository.saveAll(newMappings);
            }
        }

        return new GenerationContext(feedback, contextJsonStr, isAlreadyProcessed);
    }


    // =================================================================================
    // AI Context JSON 빌더 헬퍼 메서드 (오버페칭 방지)
    // =================================================================================

    // 1. ENTRY (첫 체결과 ENTRY 진단만 조회)
    // 파라미터로 entryDiagnoses 리스트를 직접 받도록 변경
    private String buildEntryContextJson(ExecutionSnapshot firstExec, UUID positionId, UUID userId, List<DiagnosisResult> entryDiagnoses) {
        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                FeedbackType.ENTRY_FEEDBACK.name(),
                "v1.0",
                userId,
                positionId,
                new StockDto(firstExec.getStockId(),
                firstExec.getStockSymbol(),
                firstExec.getStockName()),
                new PositionDto("OPEN", firstExec.getPositionAveragePrice(),
                firstExec.getPositionQuantityAfter(),
                firstExec.getPlannedStopLossPrice()),
                null,
                null,
                List.of(mapToExecutionDto(firstExec)),
                mapToMarketContextDto(firstExec),
                entryDiagnoses.stream().map(this::mapToDiagnosisDto).toList()
        );
        return serializeToJson(requestDto);
    }

    // 2. ON_DEMAND (전체 체결과 ENTRY/TRADE 진단 조회)
    // 파라미터로 diagnoses 리스트를 직접 받도록 변경
    private String buildOnDemandContextJson(ExecutionSnapshot latestExec, UUID positionId, UUID userId, List<DiagnosisResult> diagnoses) {
        List<ExecutionSnapshot> allExecutions = executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId);

        String previousSummary = null;
        Optional<Feedback> prevFeedbackOpt = feedbackRepository.findTopByPositionIdAndStatusOrderByCompletedAtDesc(positionId, FeedbackStatus.COMPLETED);

        if (prevFeedbackOpt.isPresent()) {
            try {
                previousSummary = objectMapper.treeToValue(prevFeedbackOpt.get().getContent(), AiFeedbackResponse.class).summary();
            } catch (Exception ignored) {}
        }

        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                FeedbackType.ON_DEMAND_FEEDBACK.name(),
                "v1.0",
                userId,
                positionId,
                new StockDto(latestExec.getStockId(), latestExec.getStockSymbol(), latestExec.getStockName()),
                new PositionDto("OPEN", latestExec.getPositionAveragePrice(), latestExec.getPositionQuantityAfter(), latestExec.getPlannedStopLossPrice()),
                null,
                previousSummary,
                allExecutions.stream().map(this::mapToExecutionDto).toList(),
                mapToMarketContextDto(latestExec),
                diagnoses.stream().map(this::mapToDiagnosisDto).toList()
        );
        return serializeToJson(requestDto);
    }

    // 3. POSITION_REVIEW (종료 정보 조회 및 전체 체결/진단)
    // 파라미터로 allDiagnoses 리스트를 직접 받도록 변경
    private String buildReviewContextJson(ExecutionSnapshot latestExec, UUID positionId, UUID userId, List<DiagnosisResult> allDiagnoses) {
        ClosedPositionSnapshot closedPos = closedPositionSnapshotRepository.findByPositionId(positionId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.CLOSED_POSITION_NOT_FOUND));

        List<ExecutionSnapshot> allExecutions = executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId);

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
                closedInfoDto, null,
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

    private boolean isProcessing(GenerationContext context) {
        return context.isAlreadyProcessed()
                && context.feedback().getStatus() == FeedbackStatus.PROCESSING
                && context.feedback().getContent() == null;
    }

    private AiFeedbackResponse awaitFeedback(CompletableFuture<AiFeedbackResponse> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }


    // [수정됨] 외부 Bean과 통신하기 위해 record를 public으로 변경
    public record GenerationContext(Feedback feedback, String contextJsonStr, boolean isAlreadyProcessed) {}
}
