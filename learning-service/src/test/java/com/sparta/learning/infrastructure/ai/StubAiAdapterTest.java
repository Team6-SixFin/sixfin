package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.config.StubAiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubAiAdapterTest {

    @Test
    @DisplayName("Stub은 검증 가능한 고정 형식의 피드백을 반환한다")
    void returnsCompleteFixedResponse() {
        StubAiProperties properties = propertiesWithNoLatency();
        properties.setFailureRate(0.0);
        StubAiAdapter adapter = new StubAiAdapter(properties);

        AiFeedbackResponse response = adapter.requestAiFeedback(
                UUID.randomUUID(), FeedbackType.ENTRY_FEEDBACK, "{}");

        assertThat(response.summary()).startsWith("[STUB]");
        assertThat(response.overview()).isNotBlank();
        assertThat(response.strengths()).isNotEmpty();
        assertThat(response.improvements()).isNotEmpty();
        assertThat(response.nextActions()).isNotEmpty();
    }

    @Test
    @DisplayName("실패율이 100%이면 실제 AI 호출과 같은 예외를 반환한다")
    void failsWhenFailureRateIsOne() {
        StubAiProperties properties = propertiesWithNoLatency();
        properties.setFailureRate(1.0);
        properties.setFailAfterLatency(false);
        StubAiAdapter adapter = new StubAiAdapter(properties);

        assertThatThrownBy(() -> adapter.requestAiFeedback(
                UUID.randomUUID(), FeedbackType.ON_DEMAND_FEEDBACK, "{}"))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED));
    }

    private StubAiProperties propertiesWithNoLatency() {
        StubAiProperties properties = new StubAiProperties();
        properties.setLatency(Duration.ZERO);
        properties.setJitter(Duration.ZERO);
        return properties;
    }
}
