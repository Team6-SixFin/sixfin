package com.sparta.user.application.dto.response;

import com.sparta.user.domain.entity.User;
import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

// 사용자 정보 응답 DTO (순수 응답 데이터)
@Getter
@AllArgsConstructor
@Schema(description = "사용자 정보")
public class UserResponseDto {
    @Schema(description = "사용자 UUID", example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1")
    private UUID userId;

    @Schema(description = "사용자 이메일", example = "member@sixfin.com")
    private String email;

    @Schema(description = "사용자 닉네임", example = "원칙지킴이")
    private String nickname;

    @Schema(description = "사용자 권한", allowableValues = {"ROLE_USER", "ROLE_ADMIN"}, example = "ROLE_USER")
    private UserRole role;

    @Schema(description = "계정 상태", allowableValues = {"ACTIVE"}, example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "회원가입 시각", example = "2026-09-07T10:30:00+09:00")
    private OffsetDateTime createdAt;

    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
