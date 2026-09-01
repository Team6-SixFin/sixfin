package com.sparta.user.application.facade;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.application.dto.response.UserResponseDto;
import com.sparta.user.application.service.UserAuthService;
import com.sparta.user.application.service.UserQueryService;
import com.sparta.user.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Presentation - Application 중계 Facade
 */
@Component
@RequiredArgsConstructor
public class UserFacade {
    private final UserAuthService userAuthService;
    private final UserQueryService userQueryService;

    public void signup(SignupRequestDto requestDto) {
        userAuthService.signup(requestDto);
    }

    public TokenResponseDto login(LoginRequestDto requestDto) {
        return userAuthService.login(requestDto);
    }

    public UserResponseDto getUserById(UUID userId) {
        return userQueryService.getUserById(userId);
    }

    public PageResponse<UserResponseDto> getUsers(UserSearchCondition condition, Pageable pageable) {
        return userQueryService.getUsers(condition, pageable);
    }
}
