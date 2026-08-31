package com.sparta.user.presentation.controller;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.application.facade.UserFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 REST Controller (/api/auth)
 * - 성공 응답 Wrapping 제거 및 HTTP Status + 순수 데이터 반환
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserFacade userFacade;

    /**
     * 회원가입 API
     * - 성공 시 201 Created 및 데이터 없이 빈 바디 반환
     */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequestDto requestDto) {
        userFacade.signup(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 로그인 API
     * - 성공 시 200 OK 및 순수 TokenResponseDto 데이터 반환
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        TokenResponseDto responseDto = userFacade.login(requestDto);
        return ResponseEntity.ok(responseDto);
    }
}
