package com.sparta.learning.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.global.response.ErrorResponse;
import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
import com.sparta.learning.infrastructure.config.StubAiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
 * 필터는 항상 등록합니다. Stub 설정이 빠졌는데도 오버라이드 헤더를 보낸 경우
 * 요청을 명시적으로 거부해 실제 Gemini로 조용히 전환되는 것을 방지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderOverrideFilter extends OncePerRequestFilter {

    private static final String PROVIDER_HEADER = "X-Ai-Provider";
    private static final String TOKEN_HEADER = "X-Ai-Provider-Token";
    private static final String STUB = "stub";
    private static final String GEMINI = "gemini";

    private final StubAiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            if (!applyOverride(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat 스레드는 재사용되므로 반드시 정리한다.
            // 빠뜨리면 다음 요청이 이전 요청의 provider 를 물려받는다.
            AiProviderContextHolder.clear();
        }
    }

    private boolean applyOverride(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestedProvider = request.getHeader(PROVIDER_HEADER);
        if (requestedProvider == null || requestedProvider.isBlank()) {
            if (request.getHeader(TOKEN_HEADER) != null) {
                writeError(response, LearningErrorCode.INVALID_AI_PROVIDER);
                return false;
            }
            return true;   // 일반 트래픽. 기본 provider 사용
        }

        String provider = requestedProvider.trim().toLowerCase();
        if (!STUB.equals(provider) && !GEMINI.equals(provider)) {
            writeError(response, LearningErrorCode.INVALID_AI_PROVIDER);
            return false;
        }

        String expectedToken = properties.getOverrideToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("AI provider 오버라이드 요청을 거부합니다. 서버에 override-token이 설정되지 않았습니다.");
            writeError(response, LearningErrorCode.AI_PROVIDER_OVERRIDE_NOT_CONFIGURED);
            return false;
        }

        String actualToken = request.getHeader(TOKEN_HEADER);
        if (!tokensEqual(expectedToken, actualToken)) {
            log.warn("AI provider 오버라이드 토큰이 일치하지 않아 요청을 거부합니다. uri={}", request.getRequestURI());
            writeError(response, LearningErrorCode.AI_PROVIDER_OVERRIDE_FORBIDDEN);
            return false;
        }

        AiProviderContextHolder.set(provider);
        log.debug("AI provider 오버라이드 적용. provider={}, uri={}", provider, request.getRequestURI());
        return true;
    }

    private boolean tokensEqual(String expectedToken, String actualToken) {
        if (actualToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                actualToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeError(HttpServletResponse response, LearningErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode, errorCode.getMessage()));
    }
}
