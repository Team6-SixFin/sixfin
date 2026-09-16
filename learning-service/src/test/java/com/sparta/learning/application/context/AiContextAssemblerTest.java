package com.sparta.learning.application.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.infrastructure.config.AiContextProperties;
import com.sparta.learning.infrastructure.persistence.projection.DiagnosisSummaryProjection;
import com.sparta.learning.infrastructure.persistence.projection.ExecutionAggregateProjection;
import com.sparta.learning.infrastructure.persistence.repository.ClosedPositionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 컨텍스트 축소 동작을 검증한다.
 *
 * 핵심 관심사는 세 가지다.
 *   1) 포지션이 커져도 컨텍스트가 상수에 수렴하는가
 *   2) 축소로 사라진 정보가 집계값과 contextScope로 보존되는가
 *   3) 피드백 타입별로 필요한 데이터만 조회하는가
 *
 * [주의] 인터페이스 프로젝션은 Mockito로 목킹하지 않고 직접 구현한다.
 * given(repo.aggregateByPositionId(...)).willReturn(mockedProjection) 형태로 쓰면
 * 인자 평가 순서 때문에 "진행 중 스터빙" 안에서 또 스터빙이 시작되어
 * UnfinishedStubbingException이 난다. getter만 있는 인터페이스라 목킹할 이유도 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiContextAssemblerTest {

    private static final UUID POSITION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock private ExecutionSnapshotRepository executionSnapshotRepository;
    @Mock private DiagnosisResultRepository diagnosisResultRepository;
    @Mock private ClosedPositionSnapshotRepository closedPositionSnapshotRepository;
    @Mock private FeedbackRepository feedbackRepository;

    private ObjectMapper objectMapper;
    private AiContextProperties properties;
    private AiContextAssembler assembler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        properties = new AiContextProperties();
        properties.setRecentExecutions(20);
        properties.setInvestmentReasonMaxChars(200);
        properties.setMaxLinkedDiagnoses(32);

        assembler = new AiContextAssembler(
                objectMapper,
                executionSnapshotRepository,
                diagnosisResultRepository,
                closedPositionSnapshotRepository,
                feedbackRepository,
                properties
        );

        // [중요] Mockito 단위 테스트는 @PostConstruct를 호출하지 않는다.
        // 직접 불러주지 않으면 contextWriter가 null이라 serialize()에서 NPE가 난다.
        assembler.initContextWriter();
    }

    // =================================================================
    // ENTRY_FEEDBACK
    // =================================================================

    @Nested
    @DisplayName("ENTRY_FEEDBACK")
    class EntryFeedback {

        @Test
        @DisplayName("최초 매수 1건뿐이므로 체결 선별·집계 쿼리를 호출하지 않는다")
        void 집계_쿼리를_치지_않는다() {
            ExecutionSnapshot firstExecution = execution(TradeType.BUY, 10, "183.1700", 1);
            givenDiagnoses(
                    diagnosis(1L, "STOP_LOSS_SET", "PASS", 1L),
                    diagnosis(2L, "HIGH_CHASING_BUY", "WARNING", 1L)
            );

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ENTRY_FEEDBACK, POSITION_ID, USER_ID, firstExecution);

            // ENTRY는 anchorExecution 하나면 충분하다. 쿼리 2개를 아끼는 분기가 살아 있는지 확인한다.
            verify(executionSnapshotRepository, never()).findContextExecutions(any(), anyInt());
            verify(executionSnapshotRepository, never()).aggregateByPositionId(any());

            JsonNode context = read(result.contextJson());
            assertThat(context.path("executions")).hasSize(1);
            assertThat(context.path("executions").get(0).path("role").asText()).isEqualTo("FIRST");
        }

        @Test
        @DisplayName("truncated=false이고 ENTRY phase만 조회한다")
        void 축소되지_않는다() {
            givenDiagnoses(diagnosis(1L, "STOP_LOSS_SET", "PASS", 1L));

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ENTRY_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "183.1700", 1));

            JsonNode scope = read(result.contextJson()).path("contextScope");
            assertThat(scope.path("truncated").asBoolean()).isFalse();
            assertThat(scope.path("totalExecutionCount").asLong()).isEqualTo(1);
            assertThat(scope.path("includedExecutionCount").asInt()).isEqualTo(1);

            verify(diagnosisResultRepository)
                    .summarizeByPositionIdAndPhases(POSITION_ID, List.of(DiagnosisPhase.ENTRY.name()));
        }
    }

    // =================================================================
    // ON_DEMAND_FEEDBACK
    // =================================================================

    @Nested
    @DisplayName("ON_DEMAND_FEEDBACK")
    class OnDemandFeedback {

        @Test
        @DisplayName("체결 1,000건이어도 executions는 선별된 건수만 담고 총 건수는 contextScope에 남는다")
        void 체결이_많아도_컨텍스트가_상수에_수렴한다() {
            givenSelectedExecutions(23);
            givenAggregate(1000L);
            givenDiagnoses(
                    diagnosis(1L, "HIGH_CHASING_BUY", "WARNING", 1L),
                    diagnosis(2L, "REPEATED_HIGH_CHASING_BUY", "WARNING", 187L),
                    diagnosis(3L, "SELL_BELOW_STOP_LOSS", "PASS", 204L)
            );
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "191.2000", 1000));

            JsonNode context = read(result.contextJson());
            JsonNode scope = context.path("contextScope");

            assertThat(context.path("executions")).hasSize(23);
            assertThat(scope.path("truncated").asBoolean()).isTrue();
            assertThat(scope.path("totalExecutionCount").asLong()).isEqualTo(1000);
            assertThat(scope.path("includedExecutionCount").asInt()).isEqualTo(23);
            assertThat(scope.path("note").asText()).contains("totalExecutionCount");
        }

        @Test
        @DisplayName("진단 592건이 규칙×결과 3그룹으로 줄지만 반복 횟수는 occurrenceCount로 보존된다")
        void 진단_반복_횟수가_보존된다() {
            givenSelectedExecutions(23);
            givenAggregate(1000L);
            givenDiagnoses(
                    diagnosis(1L, "HIGH_CHASING_BUY", "WARNING", 1L),
                    diagnosis(2L, "REPEATED_HIGH_CHASING_BUY", "WARNING", 187L),
                    diagnosis(3L, "SELL_BELOW_STOP_LOSS", "PASS", 404L)
            );
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "191.2000", 1000));

            JsonNode context = read(result.contextJson());
            JsonNode summary = context.path("diagnosisSummary");

            assertThat(summary).hasSize(3);
            assertThat(context.path("contextScope").path("totalDiagnosisCount").asLong())
                    .isEqualTo(1L + 187L + 404L);

            JsonNode repeated = summary.get(1);
            assertThat(repeated.path("ruleCode").asText()).isEqualTo("REPEATED_HIGH_CHASING_BUY");
            assertThat(repeated.path("occurrenceCount").asLong()).isEqualTo(187);
        }

        @Test
        @DisplayName("체결이 recent-executions 이하면 전량 포함되어 truncated=false")
        void 짧은_포지션은_손실이_없다() {
            givenSelectedExecutions(8);
            givenAggregate(8L);
            // 체결 8건이면 규칙도 8번 실행되므로 occurrenceCount가 1일 수 없다.
            // 기존 픽스처(1건 / 1그룹)는 1 > 1이 false라 우연히 통과하면서
            // truncated 조건에 진단 집계가 섞여 있던 버그를 비껴갔다.
            givenDiagnoses(
                    diagnosis(1L, "STOP_LOSS_SET", "PASS", 8L),
                    diagnosis(2L, "HIGH_CHASING_BUY", "WARNING", 3L)
            );
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 8));

            JsonNode scope = read(result.contextJson()).path("contextScope");
            assertThat(scope.path("truncated").asBoolean()).isFalse();
            assertThat(scope.path("note").asText()).contains("모든 체결");
        }

        @Test
        @DisplayName("ENTRY와 TRADE phase만 조회한다")
        void phase_범위가_기존_필터와_같다() {
            givenSelectedExecutions(5);
            givenAggregate(5L);
            givenDiagnoses();
            givenNoPreviousFeedback();

            assembler.assemble(FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 5));

            verify(diagnosisResultRepository).summarizeByPositionIdAndPhases(
                    POSITION_ID,
                    List.of(DiagnosisPhase.ENTRY.name(), DiagnosisPhase.TRADE.name()));
        }
    }

    // =================================================================
    // POSITION_REVIEW
    // =================================================================

    @Nested
    @DisplayName("POSITION_REVIEW")
    class PositionReview {

        @Test
        @DisplayName("closedInfo와 겹치는 executionSummary 필드는 내려보내지 않는다")
        void 숫자가_두_벌이_되지_않는다() {
            givenSelectedExecutions(23);
            givenAggregate(1000L);
            givenClosedPosition();
            givenDiagnoses(diagnosis(1L, "STOP_LOSS_ADHERENCE", "VIOLATION", 1L));

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.POSITION_REVIEW, POSITION_ID, USER_ID,
                    execution(TradeType.SELL, 10, "192.5500", 0));

            JsonNode context = read(result.contextJson());
            JsonNode summary = context.path("executionSummary");

            // Trading이 확정한 closedInfo가 권위 있는 집계다.
            // Learning이 스냅샷으로 재계산한 값을 나란히 보내면 이벤트 유실 시 두 숫자가 어긋난다.
            assertThat(summary.has("totalBuyQuantity")).isFalse();
            assertThat(summary.has("totalSellQuantity")).isFalse();
            assertThat(summary.has("averageBuyPrice")).isFalse();
            assertThat(summary.has("realizedProfit")).isFalse();
            assertThat(summary.has("firstExecutedAt")).isFalse();

            // closedInfo에 없는 값만 남는다 (AI입력명세 6.3 "실제 개별 매도 가격")
            assertThat(summary.has("buyCount")).isTrue();
            assertThat(summary.has("highestSellPrice")).isTrue();

            assertThat(context.path("closedInfo").path("totalBoughtQuantity").asLong()).isEqualTo(500);
            assertThat(context.path("position").path("status").asText()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("CLOSE를 포함한 전체 phase를 조회한다")
        void 전체_phase를_조회한다() {
            givenSelectedExecutions(5);
            givenAggregate(5L);
            givenClosedPosition();
            givenDiagnoses();

            assembler.assemble(FeedbackType.POSITION_REVIEW, POSITION_ID, USER_ID,
                    execution(TradeType.SELL, 10, "192.5500", 0));

            verify(diagnosisResultRepository).summarizeByPositionIdAndPhases(
                    POSITION_ID,
                    List.of(DiagnosisPhase.ENTRY.name(),
                            DiagnosisPhase.TRADE.name(),
                            DiagnosisPhase.CLOSE.name()));
        }
    }

    // =================================================================
    // 공통 규칙
    // =================================================================

    @Nested
    @DisplayName("공통")
    class Common {

        @Test
        @DisplayName("NOT_APPLICABLE은 diagnoses에서 빠지지만 diagnosisSummary에는 남는다")
        void 판정_불가는_집계에만_남는다() {
            givenSelectedExecutions(5);
            givenAggregate(5L);
            givenDiagnoses(
                    diagnosis(1L, "HIGH_CHASING_BUY", "WARNING", 3L),
                    diagnosis(2L, "STOP_LOSS_WIDTH", "NOT_APPLICABLE", 1L)
            );
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 5));

            JsonNode context = read(result.contextJson());

            assertThat(context.path("diagnosisSummary")).hasSize(2);
            assertThat(context.path("diagnoses")).hasSize(1);
            assertThat(context.path("diagnoses").get(0).path("ruleCode").asText())
                    .isEqualTo("HIGH_CHASING_BUY");

            // contextScope는 AI가 볼 수 있는 "그룹 수"를 말하므로 필터링 전 값이다.
            assertThat(context.path("contextScope").path("includedDiagnosisCount").asInt())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("연결 대상 진단 ID는 규칙별 대표이며 상한을 넘지 않는다")
        void 연결_진단이_상한을_넘지_않는다() {
            givenSelectedExecutions(5);
            givenAggregate(5L);

            List<DiagnosisSummaryProjection> rows = new ArrayList<>();
            for (long i = 1; i <= 40; i++) {
                rows.add(diagnosis(i, "RULE_" + i, "WARNING", 1L));
            }
            given(diagnosisResultRepository.summarizeByPositionIdAndPhases(any(), any()))
                    .willReturn(rows);
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 5));

            assertThat(result.linkedDiagnosisIds()).hasSize(32);
            assertThat(result.linkedDiagnosisIds()).startsWith(1L, 2L, 3L);
        }

        @Test
        @DisplayName("null 필드는 JSON에서 생략된다")
        void null_필드가_생략된다() {
            givenSelectedExecutions(5);
            givenAggregate(5L);
            givenDiagnoses();
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 5));

            // ON_DEMAND에는 closedInfo가 없고, 이전 피드백도 없다.
            assertThat(result.contextJson()).doesNotContain("\"closedInfo\"");
            assertThat(result.contextJson()).doesNotContain("\"previousFeedbackSummary\"");
        }

        @Test
        @DisplayName("대표 체결은 투자 근거 전문을, 최근 체결은 절단본을 싣는다")
        void 투자_근거_절단() {
            properties.setInvestmentReasonMaxChars(10);

            String longReason = "가".repeat(100);
            List<ExecutionSnapshot> executions = List.of(
                    executionWithReason(TradeType.BUY, 10, "183.0000", 1, longReason),   // FIRST
                    executionWithReason(TradeType.BUY, 5, "185.0000", 2, longReason),    // RECENT
                    executionWithReason(TradeType.BUY, 99, "186.0000", 3, longReason)    // LARGEST_BUY
            );
            given(executionSnapshotRepository.findContextExecutions(any(), anyInt()))
                    .willReturn(executions);
            givenAggregate(3L);
            givenDiagnoses();
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID, executions.get(2));

            JsonNode rows = read(result.contextJson()).path("executions");

            assertThat(rows.get(0).path("role").asText()).isEqualTo("FIRST");
            assertThat(rows.get(0).path("investmentReason").asText()).hasSize(100);

            assertThat(rows.get(1).path("role").asText()).isEqualTo("RECENT");
            assertThat(rows.get(1).path("investmentReason").asText()).endsWith("…");

            assertThat(rows.get(2).path("role").asText()).isEqualTo("LARGEST_BUY");
            assertThat(rows.get(2).path("investmentReason").asText()).hasSize(100);
        }

        @Test
        @DisplayName("executions에 순번(seq) 필드를 두지 않는다")
        void 거짓_순번을_만들지_않는다() {
            givenSelectedExecutions(5);
            givenAggregate(1000L);
            givenDiagnoses();
            givenNoPreviousFeedback();

            AiContextAssembler.AssembledContext result = assembler.assemble(
                    FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                    execution(TradeType.BUY, 10, "185.0000", 1000));

            // 선별 리스트 안의 인덱스를 seq로 주면 1,000건 포지션에서 5까지만 나와
            // contextScope.totalExecutionCount와 모순된다. 순서는 executedAt으로 충분하다.
            assertThat(read(result.contextJson()).path("executions").get(0).has("seq")).isFalse();
            assertThat(result.contextJson()).doesNotContain("\"executionId\"");
        }
    }

    @Test
    @DisplayName("체결을 전부 담았으면 진단이 집계로 접혀도 truncated=false다")
    void 진단_집계를_체결_절단으로_보고하지_않는다() {
        // 체결 5건은 recent-executions(20) 이하라 전량 포함된다.
        // 진단은 원본 12건이 규칙 × 판정결과 6그룹으로 접히지만, 버려진 진단은 없다.
        givenSelectedExecutions(5);
        givenAggregate(5L);
        givenDiagnoses(
                diagnosis(1L, "STOP_LOSS_SET", "PASS", 3L),
                diagnosis(2L, "STOP_LOSS_SET", "VIOLATION", 1L),
                diagnosis(3L, "HIGH_CHASING_BUY", "WARNING", 2L),
                diagnosis(4L, "HIGH_CHASING_BUY", "PASS", 2L),
                diagnosis(5L, "SELL_BELOW_STOP_LOSS", "PASS", 3L),
                diagnosis(6L, "STOP_LOSS_WIDTH", "WARNING", 1L)
        );
        givenNoPreviousFeedback();

        AiContextAssembler.AssembledContext result = assembler.assemble(
                FeedbackType.ON_DEMAND_FEEDBACK, POSITION_ID, USER_ID,
                execution(TradeType.BUY, 10, "185.0000", 5));

        JsonNode scope = read(result.contextJson()).path("contextScope");

        // 12 -> 6으로 접혔다는 사실 자체는 그대로 노출한다
        assertThat(scope.path("totalDiagnosisCount").asLong()).isEqualTo(12L);
        assertThat(scope.path("includedDiagnosisCount").asInt()).isEqualTo(6);
        // 다만 버려진 '체결'이 없으므로 절단이 아니다
        assertThat(scope.path("truncated").asBoolean()).isFalse();
        assertThat(scope.path("note").asText()).doesNotContain("요약본");
    }

    // =================================================================
    // given 헬퍼 — 스터빙은 여기서만 한다
    // =================================================================

    private void givenSelectedExecutions(int count) {
        List<ExecutionSnapshot> executions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            executions.add(execution(TradeType.BUY, i, "18" + (i % 10) + ".0000", i));
        }
        given(executionSnapshotRepository.findContextExecutions(any(), anyInt()))
                .willReturn(executions);
    }

    private void givenAggregate(long totalCount) {
        given(executionSnapshotRepository.aggregateByPositionId(POSITION_ID))
                .willReturn(new AggregateStub(totalCount));
    }

    private void givenDiagnoses(DiagnosisSummaryProjection... rows) {
        given(diagnosisResultRepository.summarizeByPositionIdAndPhases(any(), any()))
                .willReturn(List.of(rows));
    }

    private void givenClosedPosition() {
        given(closedPositionSnapshotRepository.findByPositionId(POSITION_ID))
                .willReturn(Optional.of(closedPosition()));
    }

    private void givenNoPreviousFeedback() {
        given(feedbackRepository.findTopByPositionIdAndStatusOrderByCompletedAtDesc(any(), any()))
                .willReturn(Optional.empty());
    }

    // =================================================================
    // 테스트 데이터
    // =================================================================

    private ExecutionSnapshot execution(TradeType tradeType, int quantity, String price, int quantityAfter) {
        return executionWithReason(tradeType, quantity, price, quantityAfter, "실적 개선 기대");
    }

    private ExecutionSnapshot executionWithReason(
            TradeType tradeType, int quantity, String price, int quantityAfter, String reason) {
        return ExecutionSnapshot.builder()
                .executionId(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .positionId(POSITION_ID)
                .userId(USER_ID)
                .stockId(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .tradeType(tradeType)
                .newPosition(quantityAfter == 1)
                .quantity(quantity)
                .executedPrice(new BigDecimal(price))
                .positionQuantityAfter(quantityAfter)
                .positionAveragePrice(new BigDecimal("184.0000"))
                .plannedStopLossPrice(new BigDecimal("174.0000"))
                .investmentReason(reason)
                .recent20dHigh(new BigDecimal("192.1200"))
                .recent20dLow(new BigDecimal("169.2100"))
                .recent5dReturnRate(new BigDecimal("7.2000"))
                .quoteAt(OffsetDateTime.of(2026, 9, 16, 2, 0, 0, 0, ZoneOffset.UTC))
                .executedAt(OffsetDateTime.of(2026, 9, 16, 2, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    private ClosedPositionSnapshot closedPosition() {
        return ClosedPositionSnapshot.builder()
                .positionId(POSITION_ID)
                .userId(USER_ID)
                .stockId(1L)
                .stockSymbol("AAPL")
                .stockName("Apple Inc.")
                .totalBoughtQuantity(500L)
                .totalSoldQuantity(500L)
                .averageEntryPrice(new BigDecimal("184.0000"))
                .averageExitPrice(new BigDecimal("190.0000"))
                .plannedStopLossPrice(new BigDecimal("174.0000"))
                .realizedProfit(new BigDecimal("3000.0000"))
                .realizedReturnRate(new BigDecimal("3.2600"))
                .openedAt(OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .closedAt(OffsetDateTime.of(2026, 9, 16, 2, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    private DiagnosisSummaryProjection diagnosis(
            long id, String ruleCode, String result, long occurrenceCount) {
        return new DiagnosisStub(id, ruleCode, result, occurrenceCount);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("컨텍스트 JSON 파싱 실패", e);
        }
    }

    // =================================================================
    // 프로젝션 테스트 더블
    //
    // Mockito 대신 직접 구현한다. 인터페이스 프로젝션은 getter만 있어 목킹할 이유가 없고,
    // given(...).willReturn(mockBuilder())처럼 쓰면 인자 평가 순서 때문에
    // 진행 중 스터빙 안에서 또 스터빙이 시작되어 UnfinishedStubbingException이 난다.
    // =================================================================

    private record AggregateStub(long totalCount) implements ExecutionAggregateProjection {

        @Override public long getTotalCount() { return totalCount; }
        @Override public long getBuyCount() { return totalCount / 2; }
        @Override public long getSellCount() { return totalCount - totalCount / 2; }

        @Override public Long getTotalBuyQuantity() { return totalCount * 10; }
        @Override public Long getTotalSellQuantity() { return totalCount * 10; }

        @Override public BigDecimal getTotalBuyAmount() { return new BigDecimal("1840000.0000"); }
        @Override public BigDecimal getTotalSellAmount() { return new BigDecimal("1900000.0000"); }

        @Override public BigDecimal getHighestBuyPrice() { return new BigDecimal("192.0000"); }
        @Override public BigDecimal getLowestBuyPrice() { return new BigDecimal("176.0000"); }
        @Override public BigDecimal getHighestSellPrice() { return new BigDecimal("195.0000"); }
        @Override public BigDecimal getLowestSellPrice() { return new BigDecimal("181.0000"); }

        @Override public BigDecimal getRealizedProfit() { return new BigDecimal("3000.0000"); }

        // [주의] OffsetDateTime이 아니라 String이다.
        // 네이티브 쿼리의 to_char가 ISO-8601 문자열을 반환하도록 되어 있다.
        // (Instant → OffsetDateTime 변환 예외를 피하기 위한 설계)
        @Override public String getFirstExecutedAt() { return "2026-06-01T00:00Z"; }
        @Override public String getLastExecutedAt() { return "2026-09-16T02:00Z"; }
    }

    private record DiagnosisStub(
            Long diagnosisId,
            String ruleCode,
            String result,
            long occurrenceCount
    ) implements DiagnosisSummaryProjection {

        @Override public Long getDiagnosisId() { return diagnosisId; }
        @Override public String getRuleCode() { return ruleCode; }
        @Override public String getResult() { return result; }
        @Override public Integer getRuleVersion() { return 1; }

        @Override public BigDecimal getMetricValue() { return new BigDecimal("99.1500"); }
        @Override public BigDecimal getThresholdValue() { return new BigDecimal("99.0000"); }

        @Override public String getMetricsJson() {
            return "{\"executedPrice\":183.17,\"recent20dHigh\":184.74}";
        }

        @Override public String getEvidenceMessage() {
            return "매수가가 최근 20일 최고가의 99.15% 수준입니다.";
        }

        @Override public long getOccurrenceCount() { return occurrenceCount; }
        @Override public BigDecimal getMaxMetricValue() { return new BigDecimal("99.8000"); }
        @Override public BigDecimal getMinMetricValue() { return new BigDecimal("98.2000"); }
    }
}