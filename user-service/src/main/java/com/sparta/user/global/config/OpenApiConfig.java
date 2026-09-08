package com.sparta.user.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SixFin User API",
                version = "v1",
                description = "SixFin 회원가입·로그인 및 사용자 조회 API 명세입니다. "
                        + "Gateway를 통한 요청에서는 JWT 인증 후 X-User-Id 헤더가 전달됩니다.",
                contact = @Contact(name = "SixFin Team"),
                license = @License(name = "SixFin Internal")
        )
)
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi userPublicApi() {
        return GroupedOpenApi.builder()
                .group("user-public-api")
                .displayName("User Public API")
                .pathsToMatch("/api/auth/**", "/api/users/**")
                .build();
    }
}
