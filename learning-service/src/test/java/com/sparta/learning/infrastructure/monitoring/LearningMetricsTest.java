package com.sparta.learning.infrastructure.monitoring;

import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeEventType;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private LearningMetrics learningMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        learningMetrics = new LearningMetrics(meterRegistry);
    }

    @Test
    @DisplayName("첫 Kafka 이벤트 전에도 처리 결과 카운터를 0으로 등록한다")
    void initializesTradeEventCounters() {
        assertThat(meterRegistry.counter(
                "learning.trade.events",
                "event_type", TradeEventType.BUY_EXECUTED.name(),
                "result", "PROCESSED"
        ).count()).isZero();
        assertThat(meterRegistry.timer(
                "learning.trade.event.processing.duration",
                "event_type", TradeEventType.BUY_EXECUTED.name(),
                "result", "PROCESSED"
        ).count()).isZero();
    }

    @Test
    @DisplayName("AI 요청 결과와 외부 호출시간을 함께 기록한다")
    void recordsAiRequestMetrics() {
        Timer.Sample sample = learningMetrics.startTimer();

        learningMetrics.recordAiRequest(FeedbackType.ENTRY_FEEDBACK, "SUCCESS", sample);

        assertThat(meterRegistry.counter(
                "learning.ai.requests",
                "feedback_type", "ENTRY_FEEDBACK",
                "result", "SUCCESS"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer(
                "learning.ai.request.duration",
                "feedback_type", "ENTRY_FEEDBACK",
                "result", "SUCCESS"
        ).count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("AI 컨텍스트의 UTF-8 바이트 크기를 기록한다")
    void recordsAiContextSize() {
        learningMetrics.recordAiContextSize(FeedbackType.ENTRY_FEEDBACK, "한글");

        assertThat(meterRegistry.summary(
                "learning.ai.context.size",
                "feedback_type", "ENTRY_FEEDBACK"
        ).totalAmount()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("비동기 대기시간이 포함된 전체 피드백 생성시간을 기록한다")
    void recordsFeedbackGenerationMetrics() {
        long startedAtNanos = System.nanoTime();

        learningMetrics.recordFeedbackGeneration(
                FeedbackType.ON_DEMAND_FEEDBACK,
                "SUCCESS",
                startedAtNanos
        );

        assertThat(meterRegistry.counter(
                "learning.feedback.generations",
                "feedback_type", "ON_DEMAND_FEEDBACK",
                "result", "SUCCESS"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer(
                "learning.feedback.generation.duration",
                "feedback_type", "ON_DEMAND_FEEDBACK",
                "result", "SUCCESS"
        ).count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("학습자료 추천을 AI 외부 호출과 분리해 기록한다")
    void recordsLearningResourceRecommendationMetrics() {
        Timer.Sample sample = learningMetrics.startTimer();

        learningMetrics.recordLearningResourceRecommendation(
                FeedbackType.POSITION_REVIEW,
                "FAILED",
                sample
        );

        assertThat(meterRegistry.counter(
                "learning.resource.recommendations",
                "feedback_type", "POSITION_REVIEW",
                "result", "FAILED"
        ).count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer(
                "learning.resource.recommendation.duration",
                "feedback_type", "POSITION_REVIEW",
                "result", "FAILED"
        ).count()).isEqualTo(1L);
    }
}
