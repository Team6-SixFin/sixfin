package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 요청 단위로 AI 어댑터를 선택하는 라우터입니다.
 *
 * [Why @Primary] AiClientPort 구현체가 여러 개가 되므로,
 * AiFeedbackProcessor 의 `private final AiClientPort aiClientPort` 가
 * 항상 이 라우터를 주입받게 됩니다. 응용 계층 코드는 한 줄도 바뀌지 않습니다.
 *
 * [Why 런타임 선택] @ConditionalOnProperty 는 기동 시점에 평가되어
 * Gemini ↔ Stub 전환마다 재배포가 필요했습니다.
 * 라우터는 호출 시점에 요청 헤더의 오버라이드를 반영할 수 있습니다.
 * 오버라이드가 없는 일반 요청과 Kafka 로 시작되는 피드백은
 * learning.ai.provider 에 설정한 기본 제공자를 사용합니다.
 */
@Slf4j
@Primary
@Component
public class AiProviderRouter implements AiClientPort {

    private static final String STUB = "stub";
    private static final String GEMINI = "gemini";

    private final GeminiAiAdapter geminiAiAdapter;
    private final ObjectProvider<StubAiAdapter> stubAiAdapterProvider;
    private final String defaultProvider;

    public AiProviderRouter(
            GeminiAiAdapter geminiAiAdapter,
            // learning.ai.stub.enabled=false 면 StubAiAdapter 빈이 아예 없다.
            // ObjectProvider 로 받아 '없을 수 있음'을 타입으로 표현한다.
            ObjectProvider<StubAiAdapter> stubAiAdapterProvider,
            @Value("${learning.ai.provider:gemini}") String defaultProvider
    ) {
        this.geminiAiAdapter = geminiAiAdapter;
        this.stubAiAdapterProvider = stubAiAdapterProvider;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public AiFeedbackResponse requestAiFeedback(UUID positionId, FeedbackType type, String contextJson) {
        return resolveAdapter().requestAiFeedback(positionId, type, contextJson);
    }

    /**
     * 우선순위: 요청 헤더 오버라이드 → 기동 시 기본값.
     *
     * Stub을 명시적으로 선택했는데 사용할 수 없는 경우에는 실패시킵니다.
     * 성능 테스트 설정 오류가 실제 Gemini 대량 호출로 이어지는 것을 막기 위함입니다.
     */
    private AiClientPort resolveAdapter() {
        String requested = AiProviderContextHolder.get();
        if (requested == null || requested.isBlank()) {
            requested = defaultProvider;
        }

        if (STUB.equalsIgnoreCase(requested)) {
            StubAiAdapter stub = stubAiAdapterProvider.getIfAvailable();
            if (stub != null) {
                return stub;
            }
            log.error("Stub AI가 선택됐지만 StubAiAdapter가 비활성화되어 있습니다.");
            throw new CustomException(LearningErrorCode.AI_STUB_NOT_AVAILABLE);
        }
        if (GEMINI.equalsIgnoreCase(requested)) {
            return geminiAiAdapter;
        }

        log.error("지원하지 않는 AI provider가 설정됐습니다. provider={}", requested);
        throw new CustomException(LearningErrorCode.INVALID_AI_PROVIDER);
    }
}
