package com.sparta.user.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 토큰 응답 DTO (순수 응답 데이터)
@Getter
@AllArgsConstructor
@Schema(description = "로그인 성공 시 발급되는 JWT 토큰")
public class TokenResponseDto {
    @Schema(description = "API 인증에 사용하는 Access Token", example = "eyJhbGciOiJIUzI1NiJ9.access-token")
    private String accessToken;

    @Schema(description = "Access Token 재발급에 사용하는 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token")
    private String refreshToken;
}
