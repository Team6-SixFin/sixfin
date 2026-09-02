package com.sparta.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("활성 계정");
//    SUSPENDED("정지 계정"),
//    DELETED("탈퇴 계정");

    private final String description;
}