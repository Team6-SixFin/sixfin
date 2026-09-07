package com.sparta.user.presentation.controller;

import com.sparta.user.application.dto.request.LoginRequestDto;
import com.sparta.user.application.dto.request.SignupRequestDto;
import com.sparta.user.application.dto.response.TokenResponseDto;
import com.sparta.user.application.facade.UserFacade;
import com.sparta.user.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "회원가입과 JWT 로그인 API입니다.")
public class UserAuthController {

    private final UserFacade userFacade;

    /**
     * 회원가입 API
     * - 성공 시 201 Created 및 데이터 없이 빈 바디 반환
     */
    @PostMapping("/signup")
    @Operation(
            summary = "회원가입",
            description = "이메일, 비밀번호, 닉네임으로 사용자를 생성합니다. 성공하면 응답 Body 없이 201 Created를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류 또는 필수 입력값 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 사용 중인 이메일 또는 닉네임",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> signup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "가입할 사용자 정보",
                    required = true
            )
            @Valid @RequestBody SignupRequestDto requestDto
    ) {
        userFacade.signup(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 로그인 API
     * - 성공 시 200 OK 및 순수 TokenResponseDto 데이터 반환
     */
    @PostMapping("/login")
    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 검증하고 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 및 토큰 발급 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 형식 오류 또는 필수 입력값 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 사용자",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<TokenResponseDto> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "로그인 정보",
                    required = true
            )
            @Valid @RequestBody LoginRequestDto requestDto
    ) {
        TokenResponseDto responseDto = userFacade.login(requestDto);
        return ResponseEntity.ok(responseDto);
    }
}
