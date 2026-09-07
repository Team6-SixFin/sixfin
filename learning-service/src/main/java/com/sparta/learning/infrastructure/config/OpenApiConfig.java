package com.sparta.learning.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Learning 서비스의 외부 HTTP API만 OpenAPI 문서로 공개
 * Kafka 이벤트 계약과 내부 통신용 엔드포인트는 별도 문서에서 관리
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SixFin Learning API",
                version = "v1",
                description = "매매 진단 결과를 바탕으로 AI 피드백을 생성하고 조회하는 Learning 서비스 API입니다. "
                        + "외부 요청의 JWT는 Gateway가 검증하며, Learning 서비스에는 X-User-Id 헤더로 사용자 식별자가 전달됩니다.",
                contact = @Contact(name = "SixFin Team"),
                license = @License(name = "Private Project")
        )
)
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi learningPublicApi() {
        return GroupedOpenApi.builder()
                .group("learning-public-api")
                .displayName("Learning Public API")
                .pathsToMatch("/api/feedbacks/**", "/api/positions/**")
                .build();
    }
}
