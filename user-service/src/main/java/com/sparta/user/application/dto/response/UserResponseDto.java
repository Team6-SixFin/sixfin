package com.sparta.user.application.dto.response;

import com.sparta.user.domain.entity.User;
import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

// 사용자 정보 응답 DTO (순수 응답 데이터)
@Getter
@AllArgsConstructor
public class UserResponseDto {
    private UUID userId;
    private String email;
    private String nickname;
    private UserRole role;
    private UserStatus status;
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