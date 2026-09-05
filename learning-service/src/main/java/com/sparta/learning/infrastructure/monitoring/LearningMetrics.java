package com.sparta.learning.infrastructure.monitoring;

import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.TradeEventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Learning 서비스의 핵심 처리 흐름을 Prometheus 메트릭으로 기록
 *
 * userId, eventId처럼 값의 종류가 계속 늘어나는 데이터는 태그로 사용하지 않음
 * 태그는 이벤트 유형, 처리 결과, 규칙 코드처럼 값의 범위가 제한된 항목만 사용
 */
@Component
@RequiredArgsConstructor
public class LearningMetrics {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final MeterRegistry meterRegistry;

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

    public void recordDiagnosisFailure(DiagnosisPhase phase, Timer.Sample sample) {
        String phaseTag = enumName(phase);

        meterRegistry.counter(
                "learning.diagnosis.runs",
                "phase", phaseTag,
                "result", FAILED
        ).increment();

        stopDiagnosisTimer(sample, phaseTag, FAILED);
    }

    private void recordTradeEvent(TradeEventType eventType, String result, Timer.Sample sample) {
        String eventTypeTag = enumName(eventType);

        meterRegistry.counter(
                "learning.trade.events",
                "event_type", eventTypeTag,
                "result", result
        ).increment();

        sample.stop(Timer.builder("learning.trade.event.processing.duration")
                .description("Kafka trade event processing duration")
                .tag("event_type", eventTypeTag)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    private void stopDiagnosisTimer(Timer.Sample sample, String phase, String result) {
        sample.stop(Timer.builder("learning.diagnosis.duration")
                .description("Rule-based diagnosis duration")
                .tag("phase", phase)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    private String enumName(Enum<?> value) {
        return value == null ? UNKNOWN : value.name();
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
