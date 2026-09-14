package com.sparta.trading.infrastructure.monitoring;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Trading 서비스의 핵심 처리 흐름을 Prometheus 메트릭으로 기록한다.
 * 태그는 발행 결과처럼 값의 범위가 제한된 항목만 사용해 카디널리티 증가를 막는다.
 */
@Component
public class TradingMetrics {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String FAILED = "FAILED";

    private final MeterRegistry meterRegistry;

    public TradingMetrics(MeterRegistry meterRegistry, OutboxEventsQueryRepository outboxEventsQueryRepository) {
        this.meterRegistry = meterRegistry;
        initializeMeters();
        registerOutboxBacklogGauge(outboxEventsQueryRepository);
    }

    /**
     * 첫 이벤트가 오기 전에 0인 시계열을 Prometheus에 노출한다.
     * 그렇지 않으면 첫 스크랩 값이 바로 1이 되어 increase()가 첫 이벤트를 증가량으로 인식하지 못할 수 있다.
     */
    private void initializeMeters() {
        for (String result : List.of(PUBLISHED, FAILED)) {
            outboxPublishCounter(result);
            outboxPublishSendTimer(result);
        }
        outboxPublishAgeTimer();
    }

    private void registerOutboxBacklogGauge(OutboxEventsQueryRepository outboxEventsQueryRepository) {
        meterRegistry.gauge(
                "trading.outbox.pending",
                outboxEventsQueryRepository,
                repository -> (double) repository.countUnpublished()
        );
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /** Kafka 발행(sendSync) 1건의 결과와 소요시간을 기록한다. */
    public void recordPublishSuccess(Timer.Sample sendSample, Instant occurredAt) {
        outboxPublishCounter(PUBLISHED).increment();
        sendSample.stop(outboxPublishSendTimer(PUBLISHED));
        outboxPublishAgeTimer().record(Duration.between(occurredAt, Instant.now()));
    }

    public void recordPublishFailure(Timer.Sample sendSample) {
        outboxPublishCounter(FAILED).increment();
        sendSample.stop(outboxPublishSendTimer(FAILED));
    }

    private io.micrometer.core.instrument.Counter outboxPublishCounter(String result) {
        return meterRegistry.counter("trading.outbox.publish", "result", result);
    }

    private Timer outboxPublishSendTimer(String result) {
        return Timer.builder("trading.outbox.publish.send.duration")
                .description("Outbox Publisher의 Kafka 동기 발행(sendSync) 자체의 소요시간")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private Timer outboxPublishAgeTimer() {
        return Timer.builder("trading.outbox.publish.age")
                .description("Outbox 이벤트 발생(occurredAt)부터 PUBLISHED 전이까지 걸린 시간 — 배치 주기·적체 지연 확인용")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
}
