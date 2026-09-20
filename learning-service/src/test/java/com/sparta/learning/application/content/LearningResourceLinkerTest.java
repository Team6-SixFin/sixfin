package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceLinkerTest {

    @Mock private FeedbackLearningResourceService feedbackLearningResourceService;

    private SimpleMeterRegistry meterRegistry;
    private LearningResourceLinker learningResourceLinker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        learningResourceLinker =
                new LearningResourceLinker(feedbackLearningResourceService, new LearningMetrics(meterRegistry));
    }

    @Test
    void 학습_자료를_연결하고_소요_시간을_기록한다() {
        Feedback feedback = createFeedback();
        when(feedbackLearningResourceService.recommendAndLink(
                anyString(), any(), any(), any(FeedbackType.class))).thenReturn(2);

        learningResourceLinker.linkAsync(feedback);

        assertThat(recommendationCount("SUCCESS")).isEqualTo(1.0);
    }

    @Test
    void 연결에_실패해도_예외를_밖으로_던지지_않는다() {
        // 전용 풀에서 돌기 전에도 삼켰지만, 풀이 분리된 뒤에도 같은 보장이 필요하다.
        // 여기서 예외가 나가면 스레드풀의 기본 예외 처리로 흘러 원인이 묻힌다.
        Feedback feedback = createFeedback();
        when(feedbackLearningResourceService.recommendAndLink(
                anyString(), any(), any(), any(FeedbackType.class)))
                .thenThrow(new IllegalStateException("YouTube 검색 실패"));

        assertThatCode(() -> learningResourceLinker.linkAsync(feedback)).doesNotThrowAnyException();
        assertThat(recommendationCount("FAILED")).isEqualTo(1.0);
    }

    private double recommendationCount(String result) {
        return meterRegistry.find("learning.resource.recommendations")
                .tag("feedback_type", FeedbackType.ON_DEMAND_FEEDBACK.name())
                .tag("result", result)
                .counter()
                .count();
    }

    private Feedback createFeedback() {
        return Feedback.builder()
                .feedbackKey("ON_DEMAND_FEEDBACK:" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .basedOnExecutionId(UUID.randomUUID())
                .feedbackType(FeedbackType.ON_DEMAND_FEEDBACK)
                .build();
    }
}
