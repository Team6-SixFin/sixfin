package com.sparta.learning.infrastructure.ai;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.config.StubAiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 성능 측정 전용 가짜 AI 어댑터입니다.
 *
 * [Why 어댑터 교체] AiClientPort 포트가 이미 있어 DIP 가 지켜져 있습니다.
 * 구현체를 하나 더 만들고 라우터가 요청 단위로 고르면
 * AiFeedbackProcessor 를 수정하지 않아도 된다.
 *
 * [Why Thread.sleep — 중요] 일부러 블로킹으로 만듭니다.
 * GeminiAiAdapter 도 ChatClient(RestClient) 기반 블로킹 I/O 라 스레드를 점유한 채 대기합니다.
 * 논블로킹으로 만들면 스레드가 즉시 반납되어 큐가 차지 않고,
 * "언제 큐가 포화되는가"라는 테스트 목적 자체가 사라집니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "learning.ai.stub.enabled", havingValue = "true")
public class StubAiAdapter implements AiClientPort {

    private final StubAiProperties properties;

    /**
     * 기동 시 경고 배너를 남깁니다.
     * 메트릭에 provider 태그가 없어 시간 구간으로 Stub/Gemini 를 구분하므로,
     * 이 로그의 타임스탬프가 측정 구간 판단의 근거가 됩니다.
     */
    @PostConstruct
    void warnStubAvailable() {
        log.warn("================================================================");
        log.warn(" [STUB AI 사용 가능 상태] 기본 동작은 여전히 Gemini 입니다.");
        log.warn(" X-Ai-Provider: stub 헤더 + 올바른 토큰이 있는 요청만 Stub 으로 처리됩니다.");
        log.warn(" latency={}, jitter={}, failureRate={}, tokenConfigured={}",
                properties.getLatency(), properties.getJitter(), properties.getFailureRate(),
                properties.getOverrideToken() != null && !properties.getOverrideToken().isBlank());
        log.warn("================================================================");
    }

    @Override
    public AiFeedbackResponse requestAiFeedback(UUID positionId, FeedbackType type, String contextJson) {
        long latencyMs = resolveLatencyMs();
        boolean willFail = properties.getFailureRate() > 0
                && ThreadLocalRandom.current().nextDouble() < properties.getFailureRate();

        // 즉시 실패는 스레드를 빨리 반납하고, 늦은 실패는 오래 점유한다. 큐 압력이 달라진다.
        if (willFail && !properties.isFailAfterLatency()) {
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }

        sleepQuietly(latencyMs);

        if (willFail) {
            log.debug("[stub-ai] 의도적 실패. positionId={}, latencyMs={}", positionId, latencyMs);
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED);
        }

        log.debug("[stub-ai] 응답 반환. positionId={}, type={}, contextChars={}, latencyMs={}",
                positionId, type, contextJson == null ? 0 : contextJson.length(), latencyMs);

        return fixedResponse(type);
    }

    private long resolveLatencyMs() {
        long base = properties.getLatency().toMillis();
        long jitter = properties.getJitter().toMillis();
        if (jitter <= 0) {
            return base;
        }
        return Math.max(0, base + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
    }

    /** 인터럽트 플래그를 복구한다. 부하 테스트 중단 시 executor 가 스레드를 정리할 수 있어야 한다. */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(LearningErrorCode.AI_RESPONSE_GENERATION_FAILED, exception);
        }
    }

    /**
     * AiFeedbackProcessor.validateAiResponse() 를 통과해야 합니다.
     * summary / overview / strengths / improvements / nextActions 가 모두 비어 있으면
     * AI_RESPONSE_INCOMPLETE 로 실패 처리되어 측정이 왜곡됩니다.
     * (reflectionQuestions 는 검증 대상이 아니지만 실제 응답과 크기를 맞추기 위해 채웁니다)
     */
    private AiFeedbackResponse fixedResponse(FeedbackType type) {
        return new AiFeedbackResponse(
                "[STUB] 손절 계획은 세웠지만 최근 고점 부근에서 매수해 진입 시점에 주의가 필요합니다.",
                "[STUB] 매수 전에 계획 손절가와 투자 근거를 기록했지만, 최근 20일 가격 범위의 상단에서 "
                        + "진입했습니다. 진입 가격 위치를 한 번 더 점검할 필요가 있습니다. feedbackType=" + type,
                List.of("[STUB] 매수 전에 계획 손절가를 설정했습니다.", "[STUB] 투자 근거를 기록했습니다."),
                List.of("[STUB] 최근 급등 이후 고점 부근에서 진입한 점을 점검해 보세요."),
                List.of("[STUB] 다음 매수 전 20일 가격 범위에서 현재가 위치를 확인하세요."),
                List.of("[STUB] 가격이 급등하지 않았더라도 같은 근거로 매수했을까요?")
        );
    }
}