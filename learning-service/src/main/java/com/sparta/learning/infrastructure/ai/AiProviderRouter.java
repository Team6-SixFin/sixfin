package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.model.FeedbackType;
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
 * 라우터는 호출 시점에 결정하므로 무중단 전환이 가능하고,
 * 오버라이드 헤더가 없는 일반 트래픽(팀원 시연, Kafka 로 시작되는 피드백)은
 * 부하 테스트 중에도 계속 Gemini 를 사용합니다.
 */
@Slf4j
@Primary
@Component
public class AiProviderRouter implements AiClientPort {

    private static final String STUB = "stub";

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
     * 우선순위: 요청 헤더 오버라이드 → 기동 시 기본값 → Gemini
     *
     * 헤더가 stub 을 요청했는데 Stub 빈이 없으면 Gemini 로 떨어집니다.
     * (stub 이 포함되지 않은 배포에 실수로 헤더를 보내도 안전하게 동작)
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
            log.warn("stub 요청이 들어왔지만 StubAiAdapter 가 비활성화되어 Gemini 로 처리합니다. "
                    + "learning.ai.stub.enabled 설정을 확인하세요.");
        }
        return geminiAiAdapter;
    }
}