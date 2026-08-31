package com.sparta.user.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 토큰 응답 DTO (순수 응답 데이터)
@Getter
@AllArgsConstructor
public class TokenResponseDto {
    private String accessToken;
    private String refreshToken;
}