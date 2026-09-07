package com.sparta.user.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

// 회원가입 요청 DTO
@Getter
@Schema(description = "회원가입 요청")
public class SignupRequestDto {
    @Schema(description = "로그인에 사용할 이메일", example = "member@sixfin.com", format = "email")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "로그인 비밀번호", example = "Sixfin!234", format = "password")
    @NotBlank
    private String password;

    @Schema(description = "서비스에서 사용할 닉네임", example = "원칙지킴이")
    @NotBlank
    private String nickname;
}
