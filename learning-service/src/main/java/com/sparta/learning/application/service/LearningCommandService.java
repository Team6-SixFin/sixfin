package com.sparta.learning.application.service;

import com.sparta.learning.application.context.AiContextAssembler;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.entity.*;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningCommandService {

    private static final String CAPACITY_FAILURE_REASON = "AI executor 포화로 요청을 제출하지 못했습니다.";

    private final FeedbackRepository feedbackRepository;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final FeedbackDiagnosisRepository feedbackDiagnosisRepository;
    private final DiagnosisResultRepository diagnosisResultRepository;

    // [신규] 컨텍스트 조립 책임을 위임
    private final AiContextAssembler aiContextAssembler;

    // [삭제됨] ObjectMapper                     → AiContextAssembler로 이동
    // [삭제됨] ClosedPositionSnapshotRepository → AiContextAssembler로 이동

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

        CompletableFuture<AiFeedbackResponse> future;
        try{
            future = aiFeedbackProcessor.processAiFeedbackAsync(context);
        }
        catch (RejectedExecutionException exception){
            markFeedbackFailed(context, FeedbackType.ON_DEMAND_FEEDBACK);
            throw new CustomException(LearningErrorCode.AI_CAPACITY_EXCEEDED);
        }

        // 사용자 API 요청은 결과를 기다리되 비동기 예외의 원인을 복원해 기존 오류 응답을 유지한다.
        return awaitFeedback(future);
    }

    /**
     * 2. 최초 매수 진입 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    public CompletableFuture<AiFeedbackResponse> createEntryFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.ENTRY_FEEDBACK)
        );
        // AI 호출만 비동기로 위임
        return submitOrMarkFailed(context,FeedbackType.ENTRY_FEEDBACK);
    }

    /**
     * 3. 포지션 종료 리뷰 피드백 생성 (Kafka 이벤트 수신 시 호출)
     */
    public CompletableFuture<AiFeedbackResponse> createPositionReviewFeedback(UUID positionId, UUID userId) {
        GenerationContext context = transactionTemplate.execute(status ->
                prepareGenerationContext(positionId, userId, FeedbackType.POSITION_REVIEW)
        );
        return submitOrMarkFailed(context, FeedbackType.POSITION_REVIEW);
    }

    // =================================================================================
    // [트랜잭션 1] 데이터를 조회하고 PENDING/PROCESSING 상태의 Feedback을 생성/반환
    // =================================================================================
    GenerationContext prepareGenerationContext(UUID positionId, UUID userId, FeedbackType feedbackType) {

        // 비동기 Executor 대기시간까지 포함한 전체 피드백 생성시간의 시작점
        // AiFeedbackProcessor가 learning.feedback.generation.duration 으로 기록한다.
        long generationStartedAtNanos = System.nanoTime();

        // ---------- 1. 기준 체결 조회 (타입별로 최초 / 최신) ----------
        // 이 조회가 userId를 함께 검증하므로 포지션 소유권 확인도 여기서 끝난다.
        ExecutionSnapshot anchorExecution = (feedbackType == FeedbackType.ENTRY_FEEDBACK)
                ? executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_FIRST_TRADE_NOT_FOUND))
                : executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_LATEST_TRADE_NOT_FOUND));

        UUID basedOnExecutionId = anchorExecution.getExecutionId();

        // ---------- 2. 컨텍스트 조립 (조회 · 집계 · 직렬화 일체) ----------
        // [변경] 기존의 switch + 빌더 3종이 모두 여기로 흡수됐다.
        //        진단 전체 조회(findAllByPositionId)와 체결 전체 조회도 여기서 사라진다.
        AiContextAssembler.AssembledContext assembled =
                aiContextAssembler.assemble(feedbackType, positionId, userId, anchorExecution);

        // ---------- 3. 멱등성 보장 검사 및 PENDING 엔티티 저장 ----------
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

        // ---------- 4. 진단 연결 ----------
        linkDiagnoses(feedback, assembled.linkedDiagnosisIds(), isAlreadyProcessed);

        return new GenerationContext(
                feedback, assembled.contextJson(), isAlreadyProcessed, generationStartedAtNanos);
    }

    /**
     * 피드백에 사용된 진단을 feedback_diagnoses에 연결한다.
     *
     * [변경 이유]
     * 기존에는 포지션의 전체 진단(최대 1,000건)을 연결했다.
     * FeedbackDiagnosis의 PK가 GenerationType.IDENTITY라서 Hibernate가 JDBC 배치 INSERT를 쓰지 못한다.
     * (생성된 키를 즉시 받아야 하므로 배치 큐에 넣을 수 없다.)
     * 1,000건이면 개별 INSERT 1,000회가 그대로 트랜잭션 안에서 실행된다.
     *
     * 이제는 "AI에 실제로 전달한 진단"(규칙 × 결과별 대표, 최대 32건)만 연결한다.
     * 진단규칙과필요한데이터명세 6.8의 "주요 경고·위반 진단 연결"에도 이쪽이 부합한다.
     *
     * 부수 효과: 피드백 상세 조회(FeedbackDetailQueryRepository.findDiagnoses)가 페이징 없이
     * 연결된 진단을 전부 반환하므로, 이 변경으로 상세 API 응답도 최대 32건으로 제한된다.
     * (학습 자료 추천은 feedback_diagnoses가 아니라 diagnosis_results를 직접 조회하므로 영향 없음)
     *
     * getReferenceById는 SELECT를 발생시키지 않는다. FK 값만 필요하므로 프록시로 충분하다.
     */
    private void linkDiagnoses(Feedback feedback, List<Long> diagnosisIds, boolean isAlreadyProcessed) {
        // 이미 처리가 끝난(또는 진행 중인) 피드백이 아닐 때만 매핑 로직 수행
        if (diagnosisIds.isEmpty() || isAlreadyProcessed) {
            return;
        }

        // 1. 이미 이 피드백에 매핑되어 DB에 저장된 진단 결과 ID를 Set으로 확보 (비교 속도 향상)
        Set<Long> existingDiagnosisIds =
                feedbackDiagnosisRepository.findAllByFeedbackId(feedback.getId()).stream()
                        .map(mapping -> mapping.getDiagnosisResult().getId())
                        .collect(Collectors.toSet());

        // 2. 아직 매핑되지 않은 것만 필터링
        List<FeedbackDiagnosis> newMappings = diagnosisIds.stream()
                .filter(id -> !existingDiagnosisIds.contains(id))
                .map(id -> FeedbackDiagnosis.builder()
                        .feedback(feedback)
                        .diagnosisResult(diagnosisResultRepository.getReferenceById(id))
                        .build())
                .toList();

        // 3. 새로운 매핑이 있을 때만 saveAll 실행
        if (!newMappings.isEmpty()) {
            feedbackDiagnosisRepository.saveAll(newMappings);
        }
    }

    /** 카프카 경로의 AI 작업제출. 거부되면 피드백을 FAILED로 내리고 이벤트는 성공 처리 */
    private CompletableFuture<AiFeedbackResponse> submitOrMarkFailed(
            GenerationContext context, FeedbackType feedbackType){
        try{
            return aiFeedbackProcessor.processAiFeedbackAsync(context);
        }
        catch (RejectedExecutionException exception){
            markFeedbackFailed(context, feedbackType);

            return CompletableFuture.completedFuture(null);
        }
    }

    /** 거부된 피드백을 재시도 가능한 상태로 되돌린다  */
    private void markFeedbackFailed(GenerationContext context, FeedbackType feedbackType){
        UUID positionId = context.feedback().getPositionId();

        if (context.isAlreadyProcessed()) {
            log.warn("[{}] AI executor 포화로 피드백 생성을 건너뜁니다. 이미 처리된 건이라 상태는 유지한다. positionId={}",
                    feedbackType, positionId);
            return;
        }

        String feedbackKey = context.feedback().getFeedbackKey();

        transactionTemplate.executeWithoutResult(status ->
                feedbackRepository.findByFeedbackKey(feedbackKey).ifPresent(feedback -> feedback.fail(CAPACITY_FAILURE_REASON))
        );

        log.warn("[{}] AI executor 포화로 피드백 생성을 건너뜁니다. positionId={}", feedbackType, positionId);
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
    public record GenerationContext(
            Feedback feedback,
            String contextJsonStr,
            boolean isAlreadyProcessed,
            long generationStartedAtNanos
    ) {
        // 단위 테스트나 외부 호출부에서 기존 3개 인자 생성 방식을 유지한다.
        public GenerationContext(Feedback feedback, String contextJsonStr, boolean isAlreadyProcessed) {
            this(feedback, contextJsonStr, isAlreadyProcessed, System.nanoTime());
        }
    }
}