package com.sparta.learning.application.service;

import com.sparta.learning.application.context.AiContextAssembler;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.entity.*;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.swing.text.html.Option;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 피드백 라이프사이클을 검증한다.
 *
 * [책임 변경] 컨텍스트 JSON의 내용(선별된 체결, 진단 집계, contextScope 등)은
 * AiContextAssembler로 분리됐으므로 AiContextAssemblerTest에서 검증한다.
 * 여기서는 기준 체결 조회 → 조립 위임 → 멱등성 → 진단 연결 → 비동기 위임 흐름만 본다.
 */
@ExtendWith(MockitoExtension.class)
class LearningCommandServiceTest {

    private LearningCommandService learningCommandService;

    @Mock private AiFeedbackProcessor aiFeedbackProcessor;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private ExecutionSnapshotRepository executionSnapshotRepository;
    @Mock private FeedbackDiagnosisRepository feedbackDiagnosisRepository;
    @Mock private DiagnosisResultRepository diagnosisResultRepository;
    @Mock private TransactionTemplate transactionTemplate;

    // [신규] 컨텍스트 조립은 이 협력자에게 위임된다.
    @Mock private AiContextAssembler aiContextAssembler;

    // [삭제됨] ObjectMapper                     → AiContextAssembler로 이동
    // [삭제됨] ClosedPositionSnapshotRepository → AiContextAssembler로 이동

    // 가짜DB 역할을 할 map
    private final java.util.Map<String, Feedback> fakeFeedbackDb = new java.util.HashMap<>();

    // 진단 결과 생성용(서비스 의존성이 아니라 테스트 픽스처 제작용)
    private final ObjectMapper testObjectMapper = new ObjectMapper();

    private UUID positionId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        positionId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // 생성자 파라미터가 7개로 바뀌었다. 필드 선언 순서와 동일해야 한다.
        learningCommandService = new LearningCommandService(
                feedbackRepository,
                executionSnapshotRepository,
                feedbackDiagnosisRepository,
                diagnosisResultRepository,
                aiContextAssembler,
                transactionTemplate,
                aiFeedbackProcessor
        );

        // TransactionTemplate Mocking (트랜잭션 실행 우회)
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // =========================================================================
    // Mock 데이터 Helper
    // =========================================================================

    private ExecutionSnapshot createExecutionSnapshot(TradeType tradeType) {
        return ExecutionSnapshot.builder()
                .executionId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .positionId(positionId)
                .userId(userId)
                .stockId(100L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .tradeType(tradeType)
                .quantity(10)
                .executedPrice(BigDecimal.valueOf(150.0))
                .positionQuantityAfter(tradeType == TradeType.BUY ? 10 : 0)
                .positionAveragePrice(BigDecimal.valueOf(150.0))
                .plannedStopLossPrice(BigDecimal.valueOf(140.0))
                .investmentReason("테스트 투자 근거")
                .recent20dHigh(BigDecimal.valueOf(160.0))
                .recent20dLow(BigDecimal.valueOf(130.0))
                .recent5dReturnRate(BigDecimal.valueOf(2.5))
                .quoteAt(OffsetDateTime.now())
                .executedAt(OffsetDateTime.now())
                .build();
    }

    private DiagnosisResult createDiagnosisResult(DiagnosisPhase phase) {
        return DiagnosisResult.builder()
                .userId(userId)
                .positionId(positionId)
                .diagnosisPhase(phase)
                .ruleCode("RULE_001")
                .ruleVersion(1)
                .result(DiagnosisStatus.PASS)
                .metricValue(BigDecimal.valueOf(5.0))
                .thresholdValue(BigDecimal.valueOf(3.0))
                .metrics(testObjectMapper.createObjectNode())
                .evidence(testObjectMapper.createObjectNode())
                .build();
    }

    // =========================================================================
    // 공통 Mock 설정
    // =========================================================================

    private void setupCommonMocksForProcess() {
        fakeFeedbackDb.clear();

        lenient().when(feedbackRepository.findByFeedbackKey(anyString())).thenAnswer(invocation -> {
            String requestedKey = invocation.getArgument(0);
            return Optional.ofNullable(fakeFeedbackDb.get(requestedKey));
        });

        lenient().when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            fakeFeedbackDb.put(feedback.getFeedbackKey(), feedback);
            return feedback;
        });

        lenient().when(feedbackDiagnosisRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // [신규] linkDiagnoses가 ID로 프록시를 얻어 FK만 채운다. SELECT는 발생하지 않는다.
        lenient().when(diagnosisResultRepository.getReferenceById(anyLong()))
                .thenAnswer(invocation -> createDiagnosisResult(DiagnosisPhase.ENTRY));

        // [신규] 컨텍스트 조립은 위임 대상이므로 결과만 고정한다.
        lenient().when(aiContextAssembler.assemble(any(), any(), any(), any()))
                .thenReturn(new AiContextAssembler.AssembledContext(
                        "{\"feedbackType\":\"STUB\"}", List.of(1L, 2L)));

        AiFeedbackResponse mockAiResponse = new AiFeedbackResponse(
                "요약", "총평", List.of("잘함"), List.of("개선점"), List.of("다음행동"), List.of("질문")
        );
        lenient().when(aiFeedbackProcessor.processAiFeedbackAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(mockAiResponse));
    }

    // =========================================================================
    // 테스트 케이스
    // =========================================================================

    @Test
    @DisplayName("ENTRY_FEEDBACK: 최초 체결을 기준으로 컨텍스트 조립을 위임하고 진단을 연결한다")
    void testCreateEntryFeedback() {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot firstExecution = createExecutionSnapshot(TradeType.BUY);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId))
                .thenReturn(Optional.of(firstExecution));

        // when
        AiFeedbackResponse response =
                learningCommandService.createEntryFeedback(positionId, userId).join();

        // then
        assertNotNull(response);
        assertEquals("요약", response.summary());

        // ENTRY는 최초 체결을 기준 체결로 삼는다
        verify(aiContextAssembler).assemble(
                eq(FeedbackType.ENTRY_FEEDBACK), eq(positionId), eq(userId), eq(firstExecution));

        // 조립기가 돌려준 진단 ID가 연결된다
        verify(feedbackDiagnosisRepository, times(1)).saveAll(any());
        verify(diagnosisResultRepository, times(2)).getReferenceById(anyLong());

        // 조립된 컨텍스트가 그대로 비동기 처리로 넘어간다
        ArgumentCaptor<LearningCommandService.GenerationContext> contextCaptor =
                ArgumentCaptor.forClass(LearningCommandService.GenerationContext.class);
        verify(aiFeedbackProcessor).processAiFeedbackAsync(contextCaptor.capture());
        assertEquals("{\"feedbackType\":\"STUB\"}", contextCaptor.getValue().contextJsonStr());
        assertEquals(firstExecution.getExecutionId(),
                contextCaptor.getValue().feedback().getBasedOnExecutionId());
    }

    @Test
    @DisplayName("ON_DEMAND_FEEDBACK: 최신 체결을 기준으로 컨텍스트 조립을 위임한다")
    void testCreateOnDemandFeedback() {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot latestExecution = createExecutionSnapshot(TradeType.SELL);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(latestExecution));

        // when
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // then
        assertNotNull(response);
        verify(aiContextAssembler).assemble(
                eq(FeedbackType.ON_DEMAND_FEEDBACK), eq(positionId), eq(userId), eq(latestExecution));
        verify(feedbackDiagnosisRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("POSITION_REVIEW: 최신 체결을 기준으로 컨텍스트 조립을 위임한다")
    void testCreatePositionReviewFeedback() {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot latestExecution = createExecutionSnapshot(TradeType.SELL);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(latestExecution));

        // when
        AiFeedbackResponse response =
                learningCommandService.createPositionReviewFeedback(positionId, userId).join();

        // then
        assertNotNull(response);

        // ClosedPositionSnapshot 조회는 AiContextAssembler 책임으로 이동했다.
        // 여기서는 타입만 정확히 넘기는지 본다.
        verify(aiContextAssembler).assemble(
                eq(FeedbackType.POSITION_REVIEW), eq(positionId), eq(userId), eq(latestExecution));
        verify(feedbackDiagnosisRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("기준 체결이 없으면 컨텍스트 조립을 시도하지 않고 예외를 던진다")
    void failsFastWhenAnchorExecutionMissing() {
        setupCommonMocksForProcess();
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId))
                .thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> learningCommandService.createEntryFeedback(positionId, userId)
        );

        assertEquals(LearningErrorCode.POSITION_FIRST_TRADE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(aiContextAssembler);
        verifyNoInteractions(aiFeedbackProcessor);
    }

    @Test
    @DisplayName("이미 처리 중이거나 완료된 피드백에는 진단을 다시 연결하지 않는다")
    void skipsDiagnosisLinkWhenAlreadyProcessed() {
        setupCommonMocksForProcess();
        ExecutionSnapshot latestExecution = createExecutionSnapshot(TradeType.BUY);
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(latestExecution));

        Feedback processingFeedback = processingFeedback(latestExecution);
        fakeFeedbackDb.put(processingFeedback.getFeedbackKey(), processingFeedback);

        assertThrows(CustomException.class,
                () -> learningCommandService.createOnDemandFeedback(positionId, userId));

        // 중복 연결을 막는 분기가 살아 있는지 확인한다
        verify(feedbackDiagnosisRepository, never()).saveAll(any());
        verify(diagnosisResultRepository, never()).getReferenceById(anyLong());
    }

    @Test
    @DisplayName("연결할 진단이 없으면 saveAll을 호출하지 않는다")
    void skipsDiagnosisLinkWhenNoDiagnoses() {
        setupCommonMocksForProcess();
        ExecutionSnapshot firstExecution = createExecutionSnapshot(TradeType.BUY);
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId))
                .thenReturn(Optional.of(firstExecution));

        when(aiContextAssembler.assemble(any(), any(), any(), any()))
                .thenReturn(new AiContextAssembler.AssembledContext("{}", List.of()));

        learningCommandService.createEntryFeedback(positionId, userId).join();

        verify(feedbackDiagnosisRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("요청형 피드백이 이미 생성 중이면 409 오류를 반환한다")
    void rejectsOnDemandFeedbackAlreadyInProgress() {
        setupCommonMocksForProcess();
        ExecutionSnapshot latestExecution = createExecutionSnapshot(TradeType.BUY);
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(latestExecution));

        Feedback processingFeedback = processingFeedback(latestExecution);
        fakeFeedbackDb.put(processingFeedback.getFeedbackKey(), processingFeedback);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> learningCommandService.createOnDemandFeedback(positionId, userId)
        );

        assertEquals(LearningErrorCode.FEEDBACK_GENERATION_IN_PROGRESS, exception.getErrorCode());
        verifyNoInteractions(aiFeedbackProcessor);
    }

    @Test
    @DisplayName("요청형 비동기 AI 오류의 CustomException을 그대로 전달한다")
    void unwrapsOnDemandAsyncCustomException() {
        setupCommonMocksForProcess();
        ExecutionSnapshot latestExecution = createExecutionSnapshot(TradeType.BUY);
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(latestExecution));

        CustomException aiFailure = new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        when(aiFeedbackProcessor.processAiFeedbackAsync(any()))
                .thenReturn(CompletableFuture.failedFuture(aiFailure));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> learningCommandService.createOnDemandFeedback(positionId, userId)
        );

        assertSame(aiFailure, exception);
    }

    @Test
    @DisplayName("요청형: Executor가 포화상태면 피드백을 FAILED로 내리고 503을 반환한다 ")
    void marksFailedAndReturnsCapacityErrorWhenOnDemandRejected(){
        setupCommonMocksForProcess();
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(createExecutionSnapshot(TradeType.BUY)));

        // 제출 시점에 거부된다. future는 만들어지지 않는다
        when(aiFeedbackProcessor.processAiFeedbackAsync(any()))
                .thenThrow(new RejectedExecutionException("AI executor 큐가 가득찼습니다"));

        // when
        CustomException exception = assertThrows(CustomException.class,
                () -> learningCommandService.createOnDemandFeedback(positionId, userId));

        // then
        assertEquals(LearningErrorCode.AI_CAPACITY_EXCEEDED, exception.getErrorCode());

        // PROCESSING으로 남으면 다음 요청이 409로 막힌다. FAILED여야 재점유가 가능함
        Feedback saved = fakeFeedbackDb.values().iterator().next();
        assertEquals(FeedbackStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getFailureReason());
    }

    @Test
    @DisplayName("요청형: FAILED로 내린 피드백은 다음 요청에서 다시 생성된다")
    void regeneratesFeedbackAfterCapacityFailure() {
        // given
        setupCommonMocksForProcess();
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(createExecutionSnapshot(TradeType.BUY)));

        AiFeedbackResponse mockAiResponse = new AiFeedbackResponse(
                "요약", "총평", List.of("잘함"), List.of("개선점"), List.of("다음행동"), List.of("질문")
        );
        // 1번째 제출은 거부되고, 2번째는 정상 처리된다
        when(aiFeedbackProcessor.processAiFeedbackAsync(any()))
                .thenThrow(new RejectedExecutionException("AI executor 큐가 가득찼습니다. "))
                .thenReturn(CompletableFuture.completedFuture(mockAiResponse));

        assertThrows(CustomException.class,
                () -> learningCommandService.createOnDemandFeedback(positionId, userId));

        // when
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // then
        // 409(FEEDBACK_GENERATION_IN_PROGRESS)가 아니라 정상 생성되어야 한다
        assertNotNull(response);
        assertEquals("요약", response.summary());
    }

    @Test
    @DisplayName("카프카: executor가 포화면 피드백을 FAILED로 내리고 예외를 전파하지 않는다")
    void swallowsRejectionOnKafkaPathAndMarksFailed() {
        setupCommonMocksForProcess();
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId))
                .thenReturn(Optional.of(createExecutionSnapshot(TradeType.BUY)));

        when(aiFeedbackProcessor.processAiFeedbackAsync(any()))
                .thenThrow(new RejectedExecutionException("AI executor 큐가 가득찼습니다. "));

        // when, 예외가 올라가면 Kafka 이벤트가 실패로 기록된다. 체결·진단은 이미 커밋된 상태라 예외가 올라가면 안된다
        AiFeedbackResponse response =
                learningCommandService.createEntryFeedback(positionId, userId).join();

        // then
        assertNull(response);

        Feedback saved = fakeFeedbackDb.values().iterator().next();
        assertEquals(FeedbackStatus.FAILED, saved.getStatus());
    }

    private Feedback processingFeedback(ExecutionSnapshot latestExecution) {
        Feedback feedback = Feedback.builder()
                .feedbackKey(String.format(
                        "%s:%s:%s",
                        FeedbackType.ON_DEMAND_FEEDBACK,
                        positionId,
                        latestExecution.getExecutionId()
                ))
                .userId(userId)
                .positionId(positionId)
                .feedbackType(FeedbackType.ON_DEMAND_FEEDBACK)
                .basedOnExecutionId(latestExecution.getExecutionId())
                .build();
        feedback.updateStatus(FeedbackStatus.PROCESSING);
        return feedback;
    }
}