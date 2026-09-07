package com.sparta.learning.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.entity.*;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningCommandServiceTest {

    private LearningCommandService learningCommandService;

    @Mock private AiClientPort aiClientPort;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private AiRequestRepository aiRequestRepository;
    @Mock private ExecutionSnapshotRepository executionSnapshotRepository;
    @Mock private DiagnosisResultRepository diagnosisResultRepository;
    @Mock private ClosedPositionSnapshotRepository closedPositionSnapshotRepository;
    @Mock private FeedbackDiagnosisRepository feedbackDiagnosisRepository; // 중간 테이블 리포지토리 추가
    @Mock private TransactionTemplate transactionTemplate;

    // 가짜DB 역할을 할 map
    private java.util.Map<String, Feedback> fakeFeedbackDb = new java.util.HashMap<>();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID positionId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        positionId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // 1. 수동 객체 주입
        learningCommandService = new LearningCommandService(
                aiClientPort,
                objectMapper,
                feedbackRepository,
                aiRequestRepository,
                executionSnapshotRepository,
                diagnosisResultRepository,
                closedPositionSnapshotRepository,
                feedbackDiagnosisRepository, // 주입
                transactionTemplate
        );

        // 2. TransactionTemplate Mocking (트랜잭션 실행 우회)
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
    // 실제 프로젝트 엔티티 기반 Mock 데이터 생성 Helper 메서드
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
                .metrics(objectMapper.createObjectNode())
                .evidence(objectMapper.createObjectNode())
                .build();
    }

    private ClosedPositionSnapshot createClosedPositionSnapshot() {
        return ClosedPositionSnapshot.builder()
                .positionId(positionId)
                .userId(userId)
                .stockId(100L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .totalBoughtQuantity(10L)
                .totalSoldQuantity(10L)
                .averageEntryPrice(BigDecimal.valueOf(150.0))
                .averageExitPrice(BigDecimal.valueOf(160.0))
                .plannedStopLossPrice(BigDecimal.valueOf(140.0))
                .realizedProfit(BigDecimal.valueOf(100.0))
                .realizedReturnRate(BigDecimal.valueOf(6.66))
                .openedAt(OffsetDateTime.now().minusDays(1))
                .closedAt(OffsetDateTime.now())
                .build();
    }

    // =========================================================================
    // 신규 로직에 맞춘 공통 Mock 설정 (가짜 DB 환경 구축)
    // =========================================================================
    private void setupCommonMocksForProcess() {
        // 테스트가 실행될 때마다 가짜 DB를 비워줍니다.
        fakeFeedbackDb.clear();

        // 1. [조회 Mock] 가짜 DB(Map)에서 키로 조회하여 반환
        lenient().when(feedbackRepository.findByFeedbackKey(anyString())).thenAnswer(invocation -> {
            String requestedKey = invocation.getArgument(0);
            return Optional.ofNullable(fakeFeedbackDb.get(requestedKey));
        });

        // 2. [저장 Mock] 전달받은 객체를 가짜 DB(Map)에 넣고 그대로 반환
        lenient().when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            fakeFeedbackDb.put(feedback.getFeedbackKey(), feedback);
            return feedback;
        });

        // 중간 테이블 매핑 데이터 저장 Mocking
        lenient().when(feedbackDiagnosisRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 최근 피드백 조회 로직 Mocking (기본적으로 empty 반환)
        lenient().when(feedbackRepository.findTopByPositionIdAndStatusOrderByCompletedAtDesc(any(UUID.class), any()))
                .thenReturn(Optional.empty());

        AiFeedbackResponse mockAiResponse = new AiFeedbackResponse(
                "요약", "총평", List.of("잘함"), List.of("개선점"), List.of("다음행동"), List.of("질문")
        );

        lenient().when(aiClientPort.requestAiFeedback(any(), any(), any())).thenReturn(mockAiResponse);
    }

    // =========================================================================
    // 테스트 케이스
    // =========================================================================

    @Test
    @DisplayName("ENTRY_FEEDBACK: 첫 번째 체결 내역과 ENTRY 진단 결과가 전달되고 저장 로직이 수행된다")
    void testCreateEntryFeedback() throws JsonProcessingException {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot exec1 = createExecutionSnapshot(TradeType.BUY);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId))
                .thenReturn(Optional.of(exec1));

        DiagnosisResult diagEntry = createDiagnosisResult(DiagnosisPhase.ENTRY);
        DiagnosisResult diagTrade = createDiagnosisResult(DiagnosisPhase.TRADE);
        when(diagnosisResultRepository.findAllByPositionId(positionId))
                .thenReturn(List.of(diagEntry, diagTrade));

        // when: 반환 타입이 CompletableFuture로 변경됨
        CompletableFuture<AiFeedbackResponse> futureResponse = learningCommandService.createEntryFeedback(positionId, userId);

        // 비동기 작업이 끝날 때까지 대기하고 결과를 가져옵니다.
        AiFeedbackResponse response = futureResponse.join();

        // then
        assertNotNull(response); // 반환값 검증
        assertEquals("요약", response.summary());

        verify(feedbackDiagnosisRepository, times(1)).saveAll(any()); // 중간 테이블 저장 검증

        ArgumentCaptor<AiFeedbackRequestDto> captor = ArgumentCaptor.forClass(AiFeedbackRequestDto.class);
        verify(objectMapper, atLeastOnce()).writeValueAsString(captor.capture());

        AiFeedbackRequestDto capturedDto = captor.getValue();
        assertEquals(1, capturedDto.executions().size());
        assertEquals(1, capturedDto.diagnoses().size());
        assertEquals("OPEN", capturedDto.position().status());
        assertNull(capturedDto.closedInfo(), "ENTRY 피드백에는 closedInfo가 없어야 합니다.");
    }

    @Test
    @DisplayName("ON_DEMAND_FEEDBACK: 전체 체결 내역과 ENTRY, TRADE 진단 결과가 전달된다")
    void testCreateOnDemandFeedback() throws JsonProcessingException {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot exec1 = createExecutionSnapshot(TradeType.BUY);
        ExecutionSnapshot exec2 = createExecutionSnapshot(TradeType.BUY);
        ExecutionSnapshot exec3 = createExecutionSnapshot(TradeType.SELL);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(exec3));

        when(executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId))
                .thenReturn(List.of(exec1, exec2, exec3));

        DiagnosisResult diagEntry = createDiagnosisResult(DiagnosisPhase.ENTRY);
        DiagnosisResult diagTrade = createDiagnosisResult(DiagnosisPhase.TRADE);
        DiagnosisResult diagClose = createDiagnosisResult(DiagnosisPhase.CLOSE);

        when(diagnosisResultRepository.findAllByPositionId(positionId))
                .thenReturn(List.of(diagEntry, diagTrade, diagClose));

        // when
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // then
        assertNotNull(response); // 반환값 검증
        verify(feedbackDiagnosisRepository, times(1)).saveAll(any()); // 중간 테이블 저장 검증

        ArgumentCaptor<AiFeedbackRequestDto> captor = ArgumentCaptor.forClass(AiFeedbackRequestDto.class);
        verify(objectMapper, atLeastOnce()).writeValueAsString(captor.capture());

        AiFeedbackRequestDto capturedDto = captor.getValue();
        assertEquals(3, capturedDto.executions().size());
        assertEquals(2, capturedDto.diagnoses().size());
        assertNull(capturedDto.closedInfo(), "ON_DEMAND 피드백에는 closedInfo가 없어야 합니다.");
    }

    @Test
    @DisplayName("POSITION_REVIEW: ClosedPositionSnapshot 활용 및 전체 데이터가 전달된다")
    void testCreatePositionReviewFeedback() throws JsonProcessingException {
        // given
        setupCommonMocksForProcess();
        ExecutionSnapshot exec1 = createExecutionSnapshot(TradeType.BUY);
        ExecutionSnapshot exec2 = createExecutionSnapshot(TradeType.SELL);

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtDescIdDesc(positionId, userId))
                .thenReturn(Optional.of(exec2));

        when(executionSnapshotRepository.findAllByPositionIdOrderByExecutedAtAscIdAsc(positionId))
                .thenReturn(List.of(exec1, exec2));

        DiagnosisResult diagEntry = createDiagnosisResult(DiagnosisPhase.ENTRY);
        DiagnosisResult diagTrade = createDiagnosisResult(DiagnosisPhase.TRADE);
        DiagnosisResult diagClose = createDiagnosisResult(DiagnosisPhase.CLOSE);
        when(diagnosisResultRepository.findAllByPositionId(positionId))
                .thenReturn(List.of(diagEntry, diagTrade, diagClose));

        ClosedPositionSnapshot closedSnapshot = createClosedPositionSnapshot();
        when(closedPositionSnapshotRepository.findByPositionId(positionId))
                .thenReturn(Optional.of(closedSnapshot));

        // when: 반환 타입이 CompletableFuture로 변경됨
        CompletableFuture<AiFeedbackResponse> futureResponse = learningCommandService.createPositionReviewFeedback(positionId, userId);

        // 비동기 작업 결과 대기 및 추출
        AiFeedbackResponse response = futureResponse.join();

        // then
        assertNotNull(response); // 반환값 검증
        verify(feedbackDiagnosisRepository, times(1)).saveAll(any()); // 중간 테이블 저장 검증

        ArgumentCaptor<AiFeedbackRequestDto> captor = ArgumentCaptor.forClass(AiFeedbackRequestDto.class);
        verify(objectMapper, atLeastOnce()).writeValueAsString(captor.capture());

        AiFeedbackRequestDto capturedDto = captor.getValue();
        verify(closedPositionSnapshotRepository, times(1)).findByPositionId(positionId);

        assertEquals(2, capturedDto.executions().size());
        assertEquals(3, capturedDto.diagnoses().size());
        assertEquals("CLOSED", capturedDto.position().status());

        assertNotNull(capturedDto.closedInfo(), "POSITION_REVIEW 피드백에는 closedInfo가 포함되어야 합니다.");
        assertEquals(BigDecimal.valueOf(160.0), capturedDto.closedInfo().averageExitPrice());
    }
}