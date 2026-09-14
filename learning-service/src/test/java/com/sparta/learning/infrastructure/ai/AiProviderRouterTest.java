package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderRouterTest {

    @Mock
    private GeminiAiAdapter geminiAiAdapter;

    @Mock
    private StubAiAdapter stubAiAdapter;

    @Mock
    private ObjectProvider<StubAiAdapter> stubAiAdapterProvider;

    @AfterEach
    void clearProviderContext() {
        AiProviderContextHolder.clear();
    }

    @Test
    @DisplayName("오버라이드가 없으면 기본 Gemini를 호출한다")
    void routesToDefaultGemini() {
        AiProviderRouter router = new AiProviderRouter(geminiAiAdapter, stubAiAdapterProvider, "gemini");
        AiFeedbackResponse expected = mock(AiFeedbackResponse.class);
        UUID positionId = UUID.randomUUID();
        when(geminiAiAdapter.requestAiFeedback(positionId, FeedbackType.ENTRY_FEEDBACK, "{}"))
                .thenReturn(expected);

        AiFeedbackResponse actual = router.requestAiFeedback(positionId, FeedbackType.ENTRY_FEEDBACK, "{}");

        assertThat(actual).isSameAs(expected);
        verify(stubAiAdapterProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("Stub 오버라이드가 비동기 컨텍스트에 있으면 Stub을 호출한다")
    void routesToRequestedStub() {
        AiProviderRouter router = new AiProviderRouter(geminiAiAdapter, stubAiAdapterProvider, "gemini");
        AiFeedbackResponse expected = mock(AiFeedbackResponse.class);
        UUID positionId = UUID.randomUUID();
        AiProviderContextHolder.set("stub");
        when(stubAiAdapterProvider.getIfAvailable()).thenReturn(stubAiAdapter);
        when(stubAiAdapter.requestAiFeedback(positionId, FeedbackType.ON_DEMAND_FEEDBACK, "{}"))
                .thenReturn(expected);

        AiFeedbackResponse actual = router.requestAiFeedback(positionId, FeedbackType.ON_DEMAND_FEEDBACK, "{}");

        assertThat(actual).isSameAs(expected);
        verify(geminiAiAdapter, never()).requestAiFeedback(positionId, FeedbackType.ON_DEMAND_FEEDBACK, "{}");
    }

    @Test
    @DisplayName("기본 provider가 Stub이면 헤더가 없는 Kafka 처리도 Stub을 호출한다")
    void routesToDefaultStub() {
        AiProviderRouter router = new AiProviderRouter(geminiAiAdapter, stubAiAdapterProvider, "stub");
        AiFeedbackResponse expected = mock(AiFeedbackResponse.class);
        UUID positionId = UUID.randomUUID();
        when(stubAiAdapterProvider.getIfAvailable()).thenReturn(stubAiAdapter);
        when(stubAiAdapter.requestAiFeedback(positionId, FeedbackType.POSITION_REVIEW, "{}"))
                .thenReturn(expected);

        AiFeedbackResponse actual = router.requestAiFeedback(positionId, FeedbackType.POSITION_REVIEW, "{}");

        assertThat(actual).isSameAs(expected);
        verify(geminiAiAdapter, never()).requestAiFeedback(positionId, FeedbackType.POSITION_REVIEW, "{}");
    }

    @Test
    @DisplayName("Stub이 선택됐지만 비활성화 상태이면 Gemini로 대체하지 않는다")
    void rejectsUnavailableStubInsteadOfFallingBackToGemini() {
        AiProviderRouter router = new AiProviderRouter(geminiAiAdapter, stubAiAdapterProvider, "stub");
        when(stubAiAdapterProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> router.requestAiFeedback(
                UUID.randomUUID(), FeedbackType.ENTRY_FEEDBACK, "{}"))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(LearningErrorCode.AI_STUB_NOT_AVAILABLE));

        verify(geminiAiAdapter, never()).requestAiFeedback(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("지원하지 않는 provider 설정을 Gemini로 처리하지 않는다")
    void rejectsUnknownProvider() {
        AiProviderRouter router = new AiProviderRouter(geminiAiAdapter, stubAiAdapterProvider, "unknown");

        assertThatThrownBy(() -> router.requestAiFeedback(
                UUID.randomUUID(), FeedbackType.ENTRY_FEEDBACK, "{}"))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(LearningErrorCode.INVALID_AI_PROVIDER));
    }
}
