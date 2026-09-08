package com.sparta.user.application.dto.request;

import com.sparta.user.domain.model.UserRole;
import com.sparta.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

// QueryDSL 검색 조건 DTO
@Getter
@Setter
@Schema(description = "사용자 목록 검색 조건. 입력하지 않은 조건은 검색에서 제외됩니다.")
public class UserSearchCondition {
    @Schema(description = "이메일 검색어", example = "member@sixfin.com")
    private String email;

    @Schema(description = "닉네임 검색어", example = "원칙지킴이")
    private String nickname;

    @Schema(description = "사용자 권한", allowableValues = {"ROLE_USER", "ROLE_ADMIN"}, example = "ROLE_USER")
    private UserRole role;

    @Schema(description = "계정 상태", allowableValues = {"ACTIVE"}, example = "ACTIVE")
    private UserStatus status;
}
