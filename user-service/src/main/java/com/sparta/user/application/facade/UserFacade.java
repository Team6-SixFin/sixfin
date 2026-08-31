package com.sparta.user.application.facade;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.application.service.UserAuthService;
import com.sparta.user.application.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
