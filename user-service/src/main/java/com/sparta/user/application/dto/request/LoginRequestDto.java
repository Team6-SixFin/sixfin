package com.sparta.user.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// 로그인 요청 DTO
@Getter
public class LoginRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}