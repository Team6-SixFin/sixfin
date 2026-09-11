package com.sparta.learning.infrastructure.web;

import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
import com.sparta.learning.infrastructure.config.StubAiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 헤더로 AI 제공자를 전환합니다.
 *
 *   X-Ai-Provider:       stub | gemini
 *   X-Ai-Provider-Token: 서버에 설정된 토큰과 일치해야 함
 *
 * [Why 필터] 컨트롤러 파라미터로 받으면 API 계약이 오염되고,
 * Kafka 로 시작되는 피드백 경로에는 적용할 수도 없습니다.
 * 필터는 HTTP 진입점에서만 동작하고, Kafka 경로는 기본 provider 를 그대로 씁니다.
 *
 * [Why 조건부 등록] stub 이 비활성화된 배포에서는 필터 자체가 존재하지 않아
 * 헤더를 아무리 보내도 무시됩니다. 운영 배포에 실수로 섞여도 무해합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "learning.ai.stub.enabled", havingValue = "true")
public class AiProviderOverrideFilter extends OncePerRequestFilter {

    private static final String PROVIDER_HEADER = "X-Ai-Provider";
    private static final String TOKEN_HEADER = "X-Ai-Provider-Token";

    private final StubAiProperties properties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            applyOverride(request);
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat 스레드는 재사용되므로 반드시 정리한다.
            // 빠뜨리면 다음 요청이 이전 요청의 provider 를 물려받는다.
            AiProviderContextHolder.clear();
        }
    }

    private void applyOverride(HttpServletRequest request) {
        String requestedProvider = request.getHeader(PROVIDER_HEADER);
        if (requestedProvider == null || requestedProvider.isBlank()) {
            return;   // 일반 트래픽. 기본 provider 사용
        }

        String expectedToken = properties.getOverrideToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("AI provider 오버라이드 요청을 무시합니다. 서버에 override-token 이 설정되지 않았습니다.");
            return;
        }

        if (!expectedToken.equals(request.getHeader(TOKEN_HEADER))) {
            log.warn("AI provider 오버라이드 토큰이 일치하지 않아 무시합니다. uri={}", request.getRequestURI());
            return;
        }

        String provider = requestedProvider.trim().toLowerCase();
        AiProviderContextHolder.set(provider);
        log.debug("AI provider 오버라이드 적용. provider={}, uri={}", provider, request.getRequestURI());
    }
}