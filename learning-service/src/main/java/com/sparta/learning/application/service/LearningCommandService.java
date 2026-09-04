package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final ClosedPositionSnapshotRepository closedPositionSnapshotRepository; // POSITION_REVIEW 용

    /**
     * 1. 요청형 매매 피드백 생성 (사용자 API 호출)
     */
    @Transactional
    public AiFeedbackResponse createOnDemandFeedback(UUID positionId, UUID userId) {
        ExecutionSnapshot snapshot = getLatestExecutionSnapshot(positionId, userId);
        return processFeedbackGeneration(positionId, userId, snapshot.getExecutionId(), FeedbackType.ON_DEMAND_FEEDBACK);
    }

    /**
     * 2. 최초 매수 진입 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    @Transactional
    public AiFeedbackResponse createEntryFeedback(UUID positionId, UUID userId) {
        ExecutionSnapshot snapshot = getFirstExecutionSnapshot(positionId, userId);
        return processFeedbackGeneration(positionId, userId, snapshot.getExecutionId(), FeedbackType.ENTRY_FEEDBACK);
    }

    /**
     * 3. 포지션 종료 리뷰 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    @Transactional
    public AiFeedbackResponse createPositionReviewFeedback(UUID positionId, UUID userId) {
        ExecutionSnapshot snapshot = getLatestExecutionSnapshot(positionId, userId);
        return processFeedbackGeneration(positionId, userId, snapshot.getExecutionId(), FeedbackType.POSITION_REVIEW);
    }

    /**
     * 공통 피드백 생성 및 AI 연동 처리 로직
     */
    AiFeedbackResponse processFeedbackGeneration(UUID positionId, UUID userId, UUID basedOnExecutionId, FeedbackType feedbackType) {

        // 1. 고유 feedbackKey 생성 (테이블 명세 규칙 : {feedback_type}:{position_id}:{based_on_execution_id})
        String feedbackKey = String.format("%s:%s:%s", feedbackType.name(), positionId, basedOnExecutionId);

        // 이미 존재하는 피드백인지 조회 (멱등성 보장)
        Optional<Feedback> existingFeedback = feedbackRepository.findByFeedbackKey(feedbackKey);
        if (existingFeedback.isPresent() && existingFeedback.get().getContent() != null) {
            log.info("이미 존재하는 피드백입니다. 기존 데이터를 반환합니다. Key: {}", feedbackKey);
            try {
                return objectMapper.treeToValue(existingFeedback.get().getContent(), AiFeedbackResponse.class);
            } catch (Exception e) {
                log.error("기존 피드백 Content 파싱 실패", e);
            }
        }

        // 2. feedbacks 테이블에 PENDING 상태로 레코드 생성 (없을 경우 새로 저장)
        Feedback feedback = existingFeedback.orElseGet(() -> {
            Feedback newFeedback = Feedback.builder()
                    .feedbackKey(feedbackKey)
                    .userId(userId)
                    .positionId(positionId)
                    .basedOnExecutionId(basedOnExecutionId)
                    .feedbackType(feedbackType)
                    .build();
            return feedbackRepository.save(newFeedback);
        });

        // 3. AI에 전달할 Context 구성 (JSON 변환)
//        String contextJsonStr = "{}";
        String contextJsonStr = buildContextJsonForAi(positionId, userId, feedbackType);
        JsonNode inputJsonNode = objectMapper.valueToTree(contextJsonStr);  // AiRequest 규격에 맞는 JsonNode 변환

        // TODO : 현재 사용 버전 하드코딩이라 추후 수정 해야 함
        // 공통 메타 정보 설정 (모델명 및 프롬프트 버전)
        String requestId = UUID.randomUUID().toString();
        String modelName = "gemini-3.5-flash"; // 사용하는 AI 모델명
        String promptVersion = "v1.0";

        AiFeedbackResponse aiResponse = null;

        try {
            // 4. AI 호출
            aiResponse = aiClientPort.requestAiFeedback(
                    positionId,
                    feedbackType,
                    contextJsonStr
            );

            // 5. 성공 시 Feedback 완료 처리
            JsonNode contentNode = objectMapper.valueToTree(aiResponse);
            feedback.complete(contentNode, true, promptVersion);

            // 6. AiRequest 정적 팩토리 메서드(success) 이용해 이력 저장
            AiRequest aiRequest = AiRequest.success(
                    feedback,
                    requestId,
                    modelName,
                    promptVersion,
                    inputJsonNode,
                    contentNode
            );
            aiRequestRepository.save(aiRequest);

        } catch (Exception e) {
            log.error("피드백 생성 실패", e);
            String failureReason = e.getMessage();

            // 7. 실패 시 엔티티의 fail() 메서드 호출하여 FAILED 상태로 전환
            feedback.fail(failureReason);

            // 8. AiRequest 이력 저장 (FAILED)
            AiRequest aiRequest = AiRequest.failed(
                    feedback,
                    requestId,
                    modelName,
                    promptVersion,
                    inputJsonNode,
                    failureReason);
            aiRequestRepository.save(aiRequest);

            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);

            // TODO : AI 답변 생성 실패시 현재는 FAIL로 저장되나, 팀원과 상의 후 수정 해야 함
//            // 7. 실패 시 Fallback 응답 구성 및 처리
//            AiFeedbackResponse fallbackResponse = new AiFeedbackResponse(
//                    "AI 분석 일시 지연",
//                    "일시적인 시스템 오류로 기본 피드백을 제공합니다.",
//                    null
//            );
//            JsonNode fallbackNode = objectMapper.valueToTree(fallbackResponse);
//
//            feedback.completeWithFallback(fallbackNode, failureReason);
//
//            // 8. AiRequest 정적 팩토리 메서드(fallback) 이용해 이력 저장
//            AiRequest aiRequest = AiRequest.fallback(
//                    feedback,
//                    requestId,
//                    modelName,
//                    promptVersion,
//                    inputJsonNode,
//                    fallbackNode,
//                    failureReason
//            );
//            aiRequestRepository.save(aiRequest);
        }

        // 생성된 최종 피드백 응답 DTO 반환
        return aiResponse;
    }


    /**
     * 최초 체결 스냅샷 조회 헬퍼 (ENTRY_FEEDBACK용)
     */
    private ExecutionSnapshot getFirstExecutionSnapshot(UUID positionId, UUID userId) {
        return executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_FIRST_TRADE_NOT_FOUND));
    }

    /**
     * 최신 체결 스냅샷 조회 헬퍼 (ON_DEMAND, POSITION_REVIEW용)
     */
    private ExecutionSnapshot getLatestExecutionSnapshot(UUID positionId, UUID userId) {
        return executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));
    }

    // 헬퍼 메서드 (AI 전달을 위함)
    private String buildContextJsonForAi(UUID positionId, UUID userId, FeedbackType type) {
        // 1. 공통 데이터 조회 (체결 스냅샷, 진단 결과)
        List<ExecutionSnapshot> allExecutions = executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId);
        if (allExecutions.isEmpty()) {
            throw new CustomException(LearningErrorCode.EXECUTION_NOT_FOUND);
        }
        List<DiagnosisResult> allDiagnoses = diagnosisResultRepository.findAllByPositionId(positionId);

        // 변수 초기화
        AiFeedbackRequestDto.StockDto stockDto = null;
        AiFeedbackRequestDto.PositionDto positionDto = null;
        List<AiFeedbackRequestDto.ExecutionDto> executionDtos = null;
        AiFeedbackRequestDto.MarketContextDto marketContextDto = null;
        List<AiFeedbackRequestDto.DiagnosisDto> diagnosisDtos = null;

        // 2. 피드백 타입별 분기 처리 및 매핑
        switch (type) {
            case ENTRY_FEEDBACK:
                // 진입 피드백: 최초 체결 내역 1건과 ENTRY 진단 결과만 전달
                ExecutionSnapshot firstExec = allExecutions.getFirst();

                stockDto = new AiFeedbackRequestDto.StockDto(firstExec.getStockId(), firstExec.getStockSymbol(), firstExec.getStockName());
                positionDto = new AiFeedbackRequestDto.PositionDto("OPEN", firstExec.getPositionAveragePrice(), firstExec.getPositionQuantityAfter(), firstExec.getPlannedStopLossPrice());
                executionDtos = List.of(mapToExecutionDto(firstExec));
                marketContextDto = mapToMarketContextDto(firstExec);

                diagnosisDtos = allDiagnoses.stream()
                        .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY)
                        .map(this::mapToDiagnosisDto)
                        .toList();
                break;

            case ON_DEMAND_FEEDBACK:
                // 중간 점검 피드백: 현재까지의 모든 체결 내역과 ENTRY, TRADE 진단 결과 전달
                ExecutionSnapshot latestExec = allExecutions.getLast();

                stockDto = new AiFeedbackRequestDto.StockDto(latestExec.getStockId(), latestExec.getStockSymbol(), latestExec.getStockName());
                positionDto = new AiFeedbackRequestDto.PositionDto("OPEN", latestExec.getPositionAveragePrice(), latestExec.getPositionQuantityAfter(), latestExec.getPlannedStopLossPrice());
                executionDtos = allExecutions.stream().map(this::mapToExecutionDto).toList();
                marketContextDto = mapToMarketContextDto(latestExec); // 가장 최근 체결 기준 시장 상황

                diagnosisDtos = allDiagnoses.stream()
                        .filter(d -> d.getDiagnosisPhase() == DiagnosisPhase.ENTRY || d.getDiagnosisPhase() == DiagnosisPhase.TRADE)
                        .map(this::mapToDiagnosisDto)
                        .toList();
                break;

            case POSITION_REVIEW:
                // 포지션 종료 피드백: closed_position_snapshots 활용 및 전체 데이터 전달
                ClosedPositionSnapshot closedPos = closedPositionSnapshotRepository.findByPositionId(positionId)
                        .orElseThrow(() -> new CustomException(LearningErrorCode.CLOSED_POSITION_NOT_FOUND));

                stockDto = new AiFeedbackRequestDto.StockDto(closedPos.getStockId(), closedPos.getStockSymbol(), closedPos.getStockName());
                positionDto = new AiFeedbackRequestDto.PositionDto("CLOSED", closedPos.getAverageEntryPrice(), 0, closedPos.getPlannedStopLossPrice());
                executionDtos = allExecutions.stream().map(this::mapToExecutionDto).toList();

                // 종료 시점의 시장 상황이 필요하다면 마지막 체결 내역 활용
                ExecutionSnapshot lastExec = allExecutions.get(allExecutions.size() - 1);
                marketContextDto = mapToMarketContextDto(lastExec);

                diagnosisDtos = allDiagnoses.stream().map(this::mapToDiagnosisDto).toList();
                break;
        }

        // 3. 최종 DTO 조립 및 직렬화
        AiFeedbackRequestDto requestDto = new AiFeedbackRequestDto(
                type.name(),
                "v1.0",
                userId,
                positionId,
                stockDto,
                positionDto,
                executionDtos,
                marketContextDto,
                diagnosisDtos
        );

        try {
            return objectMapper.writeValueAsString(requestDto);
        } catch (JsonProcessingException e) {
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }
    }

    // --- 변환용 Helper 메서드 ---
    private AiFeedbackRequestDto.ExecutionDto mapToExecutionDto(ExecutionSnapshot exec) {
        return new AiFeedbackRequestDto.ExecutionDto(
                exec.getExecutionId(),
                exec.getTradeType().name(),
                exec.getQuantity(),
                exec.getExecutedPrice(),
                exec.getPositionQuantityAfter(),
                exec.getInvestmentReason(),
                exec.getExecutedAt().toString()
        );
    }

    private AiFeedbackRequestDto.MarketContextDto mapToMarketContextDto(ExecutionSnapshot exec) {
        return new AiFeedbackRequestDto.MarketContextDto(
                exec.getRecent20dHigh(),
                exec.getRecent20dLow(),
                exec.getRecent5dReturnRate(),
                exec.getQuoteAt().toString()
        );
    }

    private AiFeedbackRequestDto.DiagnosisDto mapToDiagnosisDto(DiagnosisResult diag) {
        return new AiFeedbackRequestDto.DiagnosisDto(
                diag.getRuleCode(),
                diag.getRuleVersion(),
                diag.getResult().name(),
                diag.getMetricValue(),
                diag.getThresholdValue(),
                diag.getMetrics(),
                diag.getEvidence()
        );
    }
}
