package com.sparta.trading.infrastructure.monitoring;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingMetricsTest {

    @Mock
    private OutboxEventsQueryRepository outboxEventsQueryRepository;

    private SimpleMeterRegistry meterRegistry;
    private TradingMetrics tradingMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        tradingMetrics = new TradingMetrics(meterRegistry, outboxEventsQueryRepository);
    }

    @Test
    void preRegistersCountersAndTimersAtZeroSoFirstEventIsNotLostFromIncrease() {
        assertThat(meterRegistry.counter("trading.outbox.publish", "result", "PUBLISHED").count()).isZero();
        assertThat(meterRegistry.counter("trading.outbox.publish", "result", "FAILED").count()).isZero();
        assertThat(meterRegistry.find("trading.outbox.publish.send.duration").tag("result", "PUBLISHED").timer())
                .isNotNull();
        assertThat(meterRegistry.find("trading.outbox.publish.age").timer()).isNotNull();
    }

    @Test
    void recordPublishSuccessIncrementsPublishedCounterAndAgeTimer() {
        Timer.Sample sample = tradingMetrics.startTimer();
        Instant occurredAt = Instant.now().minus(5, ChronoUnit.SECONDS);

        tradingMetrics.recordPublishSuccess(sample, occurredAt);

        assertThat(meterRegistry.counter("trading.outbox.publish", "result", "PUBLISHED").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("trading.outbox.publish", "result", "FAILED").count()).isZero();
        assertThat(meterRegistry.find("trading.outbox.publish.age").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.find("trading.outbox.publish.send.duration").tag("result", "PUBLISHED").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordPublishFailureIncrementsFailedCounterOnly() {
        Timer.Sample sample = tradingMetrics.startTimer();

        tradingMetrics.recordPublishFailure(sample);

        assertThat(meterRegistry.counter("trading.outbox.publish", "result", "FAILED").count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("trading.outbox.publish.age").timer().count()).isZero();
    }

    @Test
    void exposesPendingBacklogAsGaugeBackedByRepositoryCount() {
        when(outboxEventsQueryRepository.countUnpublished()).thenReturn(42L);

        Double value = meterRegistry.find("trading.outbox.pending").gauge().value();

        assertThat(value).isEqualTo(42.0);
    }
}
