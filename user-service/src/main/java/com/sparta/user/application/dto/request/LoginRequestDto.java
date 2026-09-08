package com.sparta.user.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

// 로그인 요청 DTO
@Getter
@Schema(description = "로그인 요청")
public class LoginRequestDto {
    @Schema(description = "가입한 사용자 이메일", example = "member@sixfin.com", format = "email")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "사용자 비밀번호", example = "Sixfin!234", format = "password")
    @NotBlank
    private String password;
}
