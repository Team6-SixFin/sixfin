package com.sparta.learning.application.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto;
import com.sparta.learning.application.dto.request.AiFeedbackRequestDto.*;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.config.AiContextProperties;
import com.sparta.learning.infrastructure.persistence.projection.DiagnosisSummaryProjection;
import com.sparta.learning.infrastructure.persistence.projection.ExecutionAggregateProjection;
import com.sparta.learning.infrastructure.persistence.repository.ClosedPositionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AI 프롬프트에 주입할 컨텍스트 JSON을 조립한다.
 *
 * 왜 LearningCommandService에서 분리했는가:
 * 기존 서비스에 컨텍스트 조립(조회·집계·직렬화)과 피드백 라이프사이클(멱등성·상태 전이·진단 연결)이
 * 한 클래스에 섞여 있었다. 분리하면 이 클래스만 단위 테스트로 크기 비교가 가능하다.
 *
 * 설계 원칙:
 *  - 조회는 전부 "상한이 고정된" 쿼리만 쓴다. 포지션 크기와 무관하게 컨텍스트 크기가 상수에 수렴해야 한다.
 *  - 축소로 사라진 정보는 반드시 집계값 + contextScope로 보존한다. 조용히 버리면 AI가 환각한다.
 *
 * 메트릭:
 * 신규 메트릭은 추가하지 않는다. 컨텍스트 크기는 AiFeedbackProcessor가 이미
 * learning.ai.context.size 로 기록하고, 전체 피드백 시간은 learning.feedback.generation.duration 이
 * 컨텍스트 준비 시작점부터 재고 있다. 조립 내부 분해값은 아래 debug 로그로 남긴다.
 *
 * 호출 맥락 주의:
 * 이 클래스는 Kafka 컨슈머 스레드에서도 동기로 실행된다.
 * (TradeEventFacade → createEntryFeedback / createPositionReviewFeedback → prepareGenerationContext)
 * 여기서 던진 예외는 TradeEventFacade의 .exceptionally()가 잡지 못하고 컨슈머까지 올라가
 * Kafka 재시도 후 DLT로 이어진다. 배포 후 learning.trade.events.dead.letter 메트릭을 반드시 확인할 것.
 *
 * (DiagnosisService가 @Transactional로 커밋을 끝낸 뒤 호출되므로,
 *  네이티브 쿼리가 flush 안 된 진단을 놓치는 문제는 없다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiContextAssembler {

    private static final String PROMPT_VERSION = "v1.0";

    /** 초·나노초를 버리고 분 단위로 고정한다. 행당 약 10~16바이트가 절약되고 AI 판단에는 영향이 없다. */
    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX");

    private static final String TRUNCATED_NOTE =
            "executions는 전체가 아니라 대표 체결만 포함된 요약본입니다. "
                    + "총 매매 횟수는 contextScope.totalExecutionCount를 사용하세요.";
    private static final String FULL_NOTE = "전체 데이터가 포함되어 있습니다.";

    private static final List<String> ENTRY_PHASES =
            List.of(DiagnosisPhase.ENTRY.name());
    private static final List<String> OPEN_PHASES =
            List.of(DiagnosisPhase.ENTRY.name(), DiagnosisPhase.TRADE.name());
    private static final List<String> ALL_PHASES =
            List.of(DiagnosisPhase.ENTRY.name(), DiagnosisPhase.TRADE.name(), DiagnosisPhase.CLOSE.name());

    private final ObjectMapper objectMapper;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final DiagnosisResultRepository diagnosisResultRepository;
    private final ClosedPositionSnapshotRepository closedPositionSnapshotRepository;
    private final FeedbackRepository feedbackRepository;
    private final AiContextProperties properties;

    /**
     * AI 컨텍스트 전용 직렬화기. null 필드를 생략한다.
     *
     * 왜 전역 설정(spring.jackson.default-property-inclusion)을 쓰지 않는가:
     * 그 설정은 애플리케이션 전체 ObjectMapper에 걸려서
     *   - FeedbackDetailResponse.completedAt 같은 null 필드가 API 응답에서 사라지고
     *   - feedbacks.content jsonb에 저장되는 reflectionQuestions 키가 사라지고
     *   - AiProviderOverrideFilter가 내려주는 ErrorResponse.details가 사라진다.
     * 얻는 것은 컨텍스트 JSON 0.5~1KB 절감뿐이라 대가가 맞지 않는다.
     *
     * copy()는 모듈 등록(JavaTimeModule 등)을 그대로 물려받으며 기동 시 1회만 수행된다.
     * 역직렬화(readTree / treeToValue)에는 inclusion 설정이 영향을 주지 않으므로
     * 그쪽은 주입받은 objectMapper를 그대로 쓴다.
     */
    private ObjectWriter contextWriter;

    @PostConstruct
    void initContextWriter() {
        this.contextWriter = objectMapper.copy().
                setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .writer();
    }

    /**
     * @param anchorExecution ENTRY면 최초 체결, 그 외에는 최신 체결 (호출부가 이미 조회해둔 것을 재사용한다)
     * @return 직렬화된 컨텍스트와, feedback_diagnoses에 연결할 진단 ID 목록
     */
    public AssembledContext assemble(FeedbackType feedbackType,
                                     UUID positionId,
                                     UUID userId,
                                     ExecutionSnapshot anchorExecution) {

        long dbStartedAtNanos = System.nanoTime();

        // ---------- 1. 진단: 왕복 1회로 집계 + 규칙별 대표 진단을 함께 조회 ----------
        // phase 범위는 기존 Java 필터와 동일하다. ENTRY / ENTRY+TRADE / 전체.
        List<String> phases = switch (feedbackType) {
            case ENTRY_FEEDBACK -> ENTRY_PHASES;
            case ON_DEMAND_FEEDBACK -> OPEN_PHASES;
            default -> ALL_PHASES;
        };
        List<DiagnosisSummaryProjection> diagnosisRows =
                diagnosisResultRepository.summarizeByPositionIdAndPhases(positionId, phases);

        long totalDiagnosisCount = diagnosisRows.stream()
                .mapToLong(DiagnosisSummaryProjection::getOccurrenceCount)
                .sum();

        // ---------- 2. 체결 ----------
        // ENTRY_FEEDBACK은 최초 매수(isNewPosition) 시점에만 호출되므로 포지션에 체결이 1건뿐이다.
        // 축소할 것이 없으니 쿼리 2개를 아예 치지 않는다.
        List<ExecutionSnapshot> selectedExecutions;
        ExecutionAggregateProjection aggregate = null;
        long totalExecutionCount;

        if (feedbackType == FeedbackType.ENTRY_FEEDBACK) {
            selectedExecutions = List.of(anchorExecution);
            totalExecutionCount = 1L;
        } else {
            selectedExecutions = executionSnapshotRepository
                    .findContextExecutions(positionId, properties.getRecentExecutions());
            aggregate = executionSnapshotRepository.aggregateByPositionId(positionId);
            totalExecutionCount = aggregate == null ? selectedExecutions.size() : aggregate.getTotalCount();
        }

        // ---------- 3. 타입별 부가 조회 ----------
        ClosedPositionSnapshot closedPosition = null;
        String previousSummary = null;

        if (feedbackType == FeedbackType.POSITION_REVIEW) {
            closedPosition = closedPositionSnapshotRepository.findByPositionId(positionId)
                    .orElseThrow(() -> new CustomException(LearningErrorCode.CLOSED_POSITION_NOT_FOUND));
        }
        if (feedbackType == FeedbackType.ON_DEMAND_FEEDBACK) {
            previousSummary = loadPreviousFeedbackSummary(positionId);
        }

        long dbElapsedNanos = System.nanoTime() - dbStartedAtNanos;

        // ---------- 4. 조립 ----------
        long serializeStartedAtNanos = System.nanoTime();

        boolean truncated = totalExecutionCount > selectedExecutions.size()
                || totalDiagnosisCount > diagnosisRows.size();

        AiFeedbackRequestDto dto = new AiFeedbackRequestDto(
                feedbackType.name(),
                PROMPT_VERSION,
                userId,
                positionId,
                toStockDto(closedPosition, anchorExecution),
                toPositionDto(closedPosition, anchorExecution),
                toClosedInfoDto(closedPosition),
                previousSummary,
                new ContextScopeDto(
                        truncated,
                        totalExecutionCount,
                        selectedExecutions.size(),
                        totalDiagnosisCount,
                        // diagnosisSummary에는 NOT_APPLICABLE 그룹도 포함되므로
                        // AI가 볼 수 있는 진단 그룹 수는 필터링 전 행 수다.
                        // 여기에 diagnoses(필터링 후) 크기를 넣으면 truncated 계산과 앞뒤가 안 맞는다.
                        diagnosisRows.size(),
                        truncated ? TRUNCATED_NOTE : FULL_NOTE
                ),
                toExecutionSummaryDto(aggregate, closedPosition != null),
                toExecutionDtos(selectedExecutions),
                toMarketContextDto(anchorExecution),
                toDiagnosisSummaryDtos(diagnosisRows),
                toDiagnosisDtos(diagnosisRows)
        );

        String contextJson = serialize(dto);
        long serializeElapsedNanos = System.nanoTime() - serializeStartedAtNanos;

        /*
         * 성능 측정용 분해값. 신규 메트릭을 만들지 않고 로그로 남긴다.
         *
         * 측정할 때만 아래 설정으로 켜면 요청별 정확한 값을 얻을 수 있다.
         *   logging.level.com.sparta.learning.application.context: DEBUG
         *   logging.level.com.sparta.learning.infrastructure.ai: DEBUG   (StubAiAdapter의 contextChars와 교차 검증)
         *
         * contextChars는 '문자 수'다. learning.ai.context.size는 UTF-8 바이트 수라
         * 한글 비중만큼 값이 다르다. 비교할 때 단위를 혼동하지 말 것.
         */
        log.debug("AI 컨텍스트 조립 완료. type={}, totalExec={}, includedExec={}, totalDiag={}, "
                        + "diagGroups={}, contextChars={}, dbMs={}, serializeMs={}",
                feedbackType,
                totalExecutionCount,
                selectedExecutions.size(),
                totalDiagnosisCount,
                diagnosisRows.size(),
                contextJson.length(),
                dbElapsedNanos / 1_000_000.0,
                serializeElapsedNanos / 1_000_000.0);

        // feedback_diagnoses 연결 대상: 규칙별 대표 진단 (최대 32건)
        List<Long> linkedDiagnosisIds = diagnosisRows.stream()
                .map(DiagnosisSummaryProjection::getDiagnosisId)
                .limit(properties.getMaxLinkedDiagnoses())
                .toList();

        return new AssembledContext(contextJson, linkedDiagnosisIds);
    }

    // ================= 매핑 헬퍼 =================

    /**
     * [주의] 순번(seq) 필드는 두지 않는다.
     * 대표 체결만 선별하는 구조라 "전체에서 몇 번째 체결인지"를 알 수 없고,
     * 선별 리스트 안의 인덱스를 seq로 주면 1,000건 포지션에서 23번까지만 나와
     * contextScope.totalExecutionCount와 모순된다. 순서는 executedAt으로 충분하다.
     */
    private List<ExecutionDto> toExecutionDtos(List<ExecutionSnapshot> executions) {
        if (executions.isEmpty()) {
            return List.of();
        }

        // 선별 쿼리(UNION)가 "전체 중 최대 매수/매도"를 이미 포함시켰으므로,
        // 선별 집합 안에서 최대를 찾으면 전체 최대와 일치한다.
        // 동점일 때도 UNION이 executed_at ASC로 뽑고 리스트도 같은 순서라 결과가 같다.
        ExecutionSnapshot first = executions.get(0);
        ExecutionSnapshot largestBuy = executions.stream()
                .filter(e -> e.getTradeType() == TradeType.BUY)
                .max(Comparator.comparingInt(ExecutionSnapshot::getQuantity)).orElse(null);
        ExecutionSnapshot largestSell = executions.stream()
                .filter(e -> e.getTradeType() == TradeType.SELL)
                .max(Comparator.comparingInt(ExecutionSnapshot::getQuantity)).orElse(null);

        return executions.stream()
                .map(e -> {
                    String role = e == first ? "FIRST"
                            : e == largestBuy ? "LARGEST_BUY"
                              : e == largestSell ? "LARGEST_SELL"
                                : "RECENT";

                    // 투자 근거는 대표 체결만 전문을 싣는다.
                    // investment_reason은 text 무제한이라, 절단하지 않으면
                    // 축소 효과를 이 필드 하나가 상쇄한다.
                    String reason = "RECENT".equals(role)
                            ? truncate(e.getInvestmentReason(), properties.getInvestmentReasonMaxChars())
                            : e.getInvestmentReason();

                    return new ExecutionDto(
                            e.getTradeType().name(),
                            e.getQuantity(),
                            e.getExecutedPrice(),
                            e.getPositionQuantityAfter(),
                            e.getPositionAveragePrice(),
                            reason,
                            format(e.getExecutedAt()),
                            role
                    );
                })
                .toList();
    }

    /**
     * @param hasClosedInfo POSITION_REVIEW처럼 closedInfo가 이미 있는 경우 true
     *
     * POSITION_REVIEW는 Trading이 확정해 보낸 closedInfo가 권위 있는 집계다.
     * 같은 의미의 값을 Learning이 자기 스냅샷으로 다시 계산해 나란히 보내면,
     * 이벤트 유실로 스냅샷이 불완전할 때 두 숫자가 어긋나 AI가 모순된 입력을 받는다.
     * (예: closedInfo.totalBoughtQuantity=100 vs executionSummary.totalBuyQuantity=80)
     *
     * closedInfo가 있을 때는 거기에 없는 값 — 매수·매도 건수와 개별 체결가의 최대·최소 — 만 싣는다.
     * AI입력명세 6.3이 요구하는 "실제 개별 매도 가격"이 이 부분이다.
     * null 필드는 contextWriter의 NON_NULL 설정으로 JSON에서 제외된다.
     */
    private ExecutionSummaryDto toExecutionSummaryDto(ExecutionAggregateProjection a, boolean hasClosedInfo) {
        if (a == null) {
            return null;
        }
        return new ExecutionSummaryDto(
                a.getBuyCount(),
                a.getSellCount(),
                hasClosedInfo ? null : a.getTotalBuyQuantity(),
                hasClosedInfo ? null : a.getTotalSellQuantity(),
                hasClosedInfo ? null : weightedAverage(a.getTotalBuyAmount(), a.getTotalBuyQuantity()),
                hasClosedInfo ? null : weightedAverage(a.getTotalSellAmount(), a.getTotalSellQuantity()),
                a.getHighestBuyPrice(),
                a.getLowestBuyPrice(),
                a.getHighestSellPrice(),
                a.getLowestSellPrice(),
                hasClosedInfo ? null : a.getRealizedProfit(),
                hasClosedInfo ? null : format(a.getFirstExecutedAt()),
                hasClosedInfo ? null : format(a.getLastExecutedAt())
        );
    }

    /** 그룹 집계값은 여기에만 담는다. 대표 행 값과 섞지 않는다. */
    private List<DiagnosisSummaryDto> toDiagnosisSummaryDtos(List<DiagnosisSummaryProjection> rows) {
        return rows.stream()
                .map(r -> new DiagnosisSummaryDto(
                        r.getRuleCode(),
                        r.getResult(),
                        r.getOccurrenceCount(),
                        r.getMaxMetricValue(),
                        r.getMinMetricValue(),
                        r.getThresholdValue()))
                .toList();
    }

    /**
     * NOT_APPLICABLE은 "실행했으나 대상이 아님"이라 피드백 본문 작성에 기여하지 않는다.
     * 집계(diagnosisSummary)에는 남기고 근거 상세만 생략한다.
     *
     * metricValue / thresholdValue / metrics는 모두 "대표 행 1건"의 값으로 통일한다.
     * 여기에 그룹 최댓값을 넣으면 metrics 안의 같은 지표와 값이 달라져 모순이 된다.
     */
    private List<DiagnosisDto> toDiagnosisDtos(List<DiagnosisSummaryProjection> rows) {
        return rows.stream()
                .filter(r -> !DiagnosisStatus.NOT_APPLICABLE.name().equals(r.getResult()))
                .map(r -> new DiagnosisDto(
                        r.getRuleCode(),
                        r.getRuleVersion(),
                        r.getResult(),
                        r.getMetricValue(),
                        r.getThresholdValue(),
                        readTree(r.getMetricsJson()),
                        r.getEvidenceMessage()))
                .toList();
    }

    private StockDto toStockDto(ClosedPositionSnapshot closed, ExecutionSnapshot exec) {
        return closed != null
                ? new StockDto(closed.getStockId(), closed.getStockSymbol(), closed.getStockName())
                : new StockDto(exec.getStockId(), exec.getStockSymbol(), exec.getStockName());
    }

    private PositionDto toPositionDto(ClosedPositionSnapshot closed, ExecutionSnapshot exec) {
        return closed != null
                ? new PositionDto("CLOSED", closed.getAverageEntryPrice(), 0, closed.getPlannedStopLossPrice())
                : new PositionDto("OPEN", exec.getPositionAveragePrice(),
                exec.getPositionQuantityAfter(), exec.getPlannedStopLossPrice());
    }

    private ClosedInfoDto toClosedInfoDto(ClosedPositionSnapshot c) {
        if (c == null) {
            return null;
        }
        return new ClosedInfoDto(
                c.getAverageExitPrice(), c.getTotalBoughtQuantity(), c.getTotalSoldQuantity(),
                c.getRealizedProfit(), c.getRealizedReturnRate(),
                format(c.getOpenedAt()), format(c.getClosedAt()));
    }

    private MarketContextDto toMarketContextDto(ExecutionSnapshot e) {
        return new MarketContextDto(e.getRecent20dHigh(), e.getRecent20dLow(),
                e.getRecent5dReturnRate(), format(e.getQuoteAt()));
    }

    private String loadPreviousFeedbackSummary(UUID positionId) {
        return feedbackRepository
                .findTopByPositionIdAndStatusOrderByCompletedAtDesc(positionId, FeedbackStatus.COMPLETED)
                .map(Feedback::getContent)
                .map(content -> {
                    try {
                        return objectMapper.treeToValue(content, AiFeedbackResponse.class).summary();
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /** jsonb를 text로 받아온 문자열을 JsonNode로 되돌린다. 파싱 실패해도 피드백 생성은 계속한다. */
    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("진단 metrics 파싱에 실패해 해당 필드를 생략합니다.", e);
            return null;
        }
    }

    private BigDecimal weightedAverage(BigDecimal totalAmount, Long totalQuantity) {
        if (totalAmount == null || totalQuantity == null || totalQuantity == 0L) {
            return null;
        }
        return totalAmount.divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP);
    }

    private String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "…";
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(MINUTE_FORMAT);
    }

    private String serialize(AiFeedbackRequestDto dto) {
        try {
            return contextWriter.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("AI 요청 JSON 직렬화 실패: positionId={}", dto.positionId(), e);
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED, e);
        }
    }

    public record AssembledContext(String contextJson, List<Long> linkedDiagnosisIds) {}
}