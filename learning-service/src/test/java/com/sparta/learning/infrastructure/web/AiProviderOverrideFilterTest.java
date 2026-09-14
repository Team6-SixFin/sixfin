package com.sparta.learning.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
import com.sparta.learning.infrastructure.config.StubAiProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderOverrideFilterTest {

    private StubAiProperties properties;
    private AiProviderOverrideFilter filter;

    @BeforeEach
    void setUp() {
        properties = new StubAiProperties();
        properties.setOverrideToken("performance-secret");
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new AiProviderOverrideFilter(properties, objectMapper);
    }

    @AfterEach
    void clearProviderContext() {
        AiProviderContextHolder.clear();
    }

    @Test
    @DisplayName("올바른 provider와 토큰은 요청 처리 중에만 컨텍스트에 저장한다")
    void appliesAndClearsValidOverride() throws Exception {
        MockHttpServletRequest request = requestWithHeaders("stub", "performance-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> providerInsideChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                providerInsideChain.set(AiProviderContextHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(providerInsideChain.get()).isEqualTo("stub");
        assertThat(AiProviderContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("잘못된 토큰은 요청을 중단하고 Gemini 기본 경로로 넘기지 않는다")
    void rejectsInvalidToken() throws Exception {
        MockHttpServletRequest request = requestWithHeaders("stub", "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AI_PROVIDER_OVERRIDE_FORBIDDEN");
        assertThat(chainInvoked).isFalse();
    }

    @Test
    @DisplayName("provider 없이 토큰만 보낸 잘못된 성능 테스트 요청을 거부한다")
    void rejectsTokenWithoutProvider() throws Exception {
        MockHttpServletRequest request = requestWithHeaders(null, "performance-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("INVALID_AI_PROVIDER");
        assertThat(chainInvoked).isFalse();
    }

    @Test
    @DisplayName("서버에 오버라이드 토큰이 없으면 설정 오류로 거부한다")
    void rejectsOverrideWhenServerTokenIsMissing() throws Exception {
        properties.setOverrideToken("");
        MockHttpServletRequest request = requestWithHeaders("stub", "performance-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AI_PROVIDER_OVERRIDE_NOT_CONFIGURED");
        assertThat(chainInvoked).isFalse();
    }

    private MockHttpServletRequest requestWithHeaders(String provider, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/positions/test/feedbacks");
        if (provider != null) {
            request.addHeader("X-Ai-Provider", provider);
        }
        if (token != null) {
            request.addHeader("X-Ai-Provider-Token", token);
        }
        return request;
    }
}
