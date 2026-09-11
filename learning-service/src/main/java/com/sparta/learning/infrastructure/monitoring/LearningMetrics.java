package com.sparta.learning.infrastructure.monitoring;

import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Learning 서비스의 핵심 처리 흐름을 Prometheus 메트릭으로 기록
 *
 * userId, eventId처럼 값의 종류가 계속 늘어나는 데이터는 태그로 사용하지 않음
 * 태그는 이벤트 유형, 처리 결과, 규칙 코드처럼 값의 범위가 제한된 항목만 사용
 */
@Component
public class LearningMetrics {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String PROCESSED = "PROCESSED";
    private static final String DUPLICATE = "DUPLICATE";
    private static final String ALREADY_PROCESSED = "ALREADY_PROCESSED";

    private final MeterRegistry meterRegistry;

    public LearningMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMeters();
    }

    /**
     * 첫 이벤트가 오기 전에 0인 시계열을 Prometheus에 노출한다.
     * 그렇지 않으면 첫 스크랩 값이 바로 1이 되어 increase()가 첫 이벤트를 증가량으로 인식하지 못할 수 있다.
     */
    private void initializeMeters() {
        for (TradeEventType eventType : TradeEventType.values()) {
            for (String result : List.of(PROCESSED, DUPLICATE, FAILED)) {
                tradeEventCounter(eventType, result);
                tradeEventTimer(eventType, result);
            }
        }

        for (FeedbackType feedbackType : FeedbackType.values()) {
            for (String result : List.of(SUCCESS, FAILED)) {
                aiRequestCounter(feedbackType, result);
                aiRequestTimer(feedbackType, result);
                learningResourceCounter(feedbackType, result);
                learningResourceTimer(feedbackType, result);
            }

            for (String result : List.of(SUCCESS, FAILED, ALREADY_PROCESSED)) {
                feedbackGenerationCounter(feedbackType, result);
                feedbackGenerationTimer(feedbackType, result);
            }
        }
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTradeEvent(
            TradeEventType eventType,
            EventIngestionResult result,
            Timer.Sample sample
    ) {
        recordTradeEvent(eventType, result.name(), sample);
    }

    public void recordTradeEventFailure(TradeEventType eventType, Timer.Sample sample) {
        recordTradeEvent(eventType, FAILED, sample);
    }

    public void recordDiagnosisSuccess(
            DiagnosisPhase phase,
            List<DiagnosisResult> savedResults,
            Timer.Sample sample
    ) {
        String phaseTag = enumName(phase);

        meterRegistry.counter(
                "learning.diagnosis.runs",
                "phase", phaseTag,
                "result", SUCCESS
        ).increment();

        savedResults.forEach(result -> meterRegistry.counter(
                "learning.diagnosis.results",
                "phase", phaseTag,
                "rule_code", valueOrUnknown(result.getRuleCode()),
                "result", enumName(result.getResult())
        ).increment());

        stopDiagnosisTimer(sample, phaseTag, SUCCESS);
    }

    // 재시도를 모두 소진해 DLT로 보낸 이벤트를 기록한다
    public void recordDeadLetter(TradeEventType eventType, Throwable cause) {
        meterRegistry.counter(  // 이 값이 0보다 크면 진단 또는 수집이 누락된 이벤트가 있다는 뜻이므로 알림 대상
                "learning.trade.events.dead.letter",
                "event_type", enumName(eventType),
                "exception", cause == null ? UNKNOWN : cause.getClass().getSimpleName() // 예외 메시지는 값이 계속 달라지므로 클래스 이름만 태그로 사용
        ).increment();
    }

    public void recordDiagnosisFailure(DiagnosisPhase phase, Timer.Sample sample) {
        String phaseTag = enumName(phase);

        meterRegistry.counter(
                "learning.diagnosis.runs",
                "phase", phaseTag,
                "result", FAILED
        ).increment();

        stopDiagnosisTimer(sample, phaseTag, FAILED);
    }

    /** Gemini에 보낸 논리적인 요청 한 건의 결과와 소요시간을 기록한다. */
    public void recordAiRequest(FeedbackType feedbackType, String result, Timer.Sample sample) {
        aiRequestCounter(feedbackType, result).increment();
        sample.stop(aiRequestTimer(feedbackType, result));
    }

    /** 프롬프트에 주입되는 JSON 크기를 기록해 장기 포지션 컨텍스트 축소 효과를 비교한다. */
    public void recordAiContextSize(FeedbackType feedbackType, String contextJson) {
        int contextBytes = contextJson == null
                ? 0
                : contextJson.getBytes(StandardCharsets.UTF_8).length;

        DistributionSummary.builder("learning.ai.context.size")
                .description("AI feedback context JSON size")
                .baseUnit("bytes")
                .tag("feedback_type", enumName(feedbackType))
                .register(meterRegistry)
                .record(contextBytes);
    }

    /** 컨텍스트 준비 시작부터 AI·저장·학습자료 연결이 끝날 때까지의 시간을 기록한다. */
    public void recordFeedbackGeneration(
            FeedbackType feedbackType,
            String result,
            long startedAtNanos
    ) {
        feedbackGenerationCounter(feedbackType, result).increment();
        feedbackGenerationTimer(feedbackType, result)
                .record(Math.max(0, System.nanoTime() - startedAtNanos), TimeUnit.NANOSECONDS);
    }

    /** 교육 자료 추천 구간을 별도로 측정해 AI 호출시간과 외부 콘텐츠 검색시간을 구분한다. */
    public void recordLearningResourceRecommendation(
            FeedbackType feedbackType,
            String result,
            Timer.Sample sample
    ) {
        learningResourceCounter(feedbackType, result).increment();
        sample.stop(learningResourceTimer(feedbackType, result));
    }

    private void recordTradeEvent(TradeEventType eventType, String result, Timer.Sample sample) {
        tradeEventCounter(eventType, result).increment();
        sample.stop(tradeEventTimer(eventType, result));
    }

    private void stopDiagnosisTimer(Timer.Sample sample, String phase, String result) {
        sample.stop(Timer.builder("learning.diagnosis.duration")
                .description("Rule-based diagnosis duration")
                .tag("phase", phase)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    private Counter tradeEventCounter(TradeEventType eventType, String result) {
        return meterRegistry.counter(
                "learning.trade.events",
                "event_type", enumName(eventType),
                "result", valueOrUnknown(result)
        );
    }

    private Timer tradeEventTimer(TradeEventType eventType, String result) {
        return Timer.builder("learning.trade.event.processing.duration")
                .description("Kafka trade event processing duration")
                .tag("event_type", enumName(eventType))
                .tag("result", valueOrUnknown(result))
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Counter aiRequestCounter(FeedbackType feedbackType, String result) {
        return meterRegistry.counter(
                "learning.ai.requests",
                "feedback_type", enumName(feedbackType),
                "result", valueOrUnknown(result)
        );
    }

    private Timer aiRequestTimer(FeedbackType feedbackType, String result) {
        return Timer.builder("learning.ai.request.duration")
                .description("AI provider request and structured response validation duration")
                .tag("feedback_type", enumName(feedbackType))
                .tag("result", valueOrUnknown(result))
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Counter feedbackGenerationCounter(
            FeedbackType feedbackType,
            String result
    ) {
        return meterRegistry.counter(
                "learning.feedback.generations",
                "feedback_type", enumName(feedbackType),
                "result", valueOrUnknown(result)
        );
    }

    private Timer feedbackGenerationTimer(FeedbackType feedbackType, String result) {
        return Timer.builder("learning.feedback.generation.duration")
                .description("Feedback generation duration including async queue, AI, persistence, and resources")
                .tag("feedback_type", enumName(feedbackType))
                .tag("result", valueOrUnknown(result))
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Counter learningResourceCounter(
            FeedbackType feedbackType,
            String result
    ) {
        return meterRegistry.counter(
                "learning.resource.recommendations",
                "feedback_type", enumName(feedbackType),
                "result", valueOrUnknown(result)
        );
    }

    private Timer learningResourceTimer(FeedbackType feedbackType, String result) {
        return Timer.builder("learning.resource.recommendation.duration")
                .description("Learning resource recommendation and linking duration")
                .tag("feedback_type", enumName(feedbackType))
                .tag("result", valueOrUnknown(result))
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private String enumName(Enum<?> value) {
        return value == null ? UNKNOWN : value.name();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
