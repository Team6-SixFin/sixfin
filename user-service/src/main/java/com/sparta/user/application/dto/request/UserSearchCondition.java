package com.sparta.user.application.dto.request;

import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import lombok.Getter;
import lombok.Setter;

// QueryDSL 검색 조건 DTO
@Getter
@Setter
public class UserSearchCondition {
    private String email;
    private String nickname;
    private UserRole role;
    private UserStatus status;
}