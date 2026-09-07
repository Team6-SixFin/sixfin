package com.sparta.user.presentation.controller;

import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.application.dto.response.UserResponseDto;
import com.sparta.user.application.facade.UserFacade;
import com.sparta.user.application.service.UserQueryService;
import com.sparta.user.global.response.ErrorResponse;
import com.sparta.user.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


/**
 * 사용자 정보 조회 REST Controller (/api/users)
 * - 성공 응답 Wrapping 제거 및 HTTP Status + 순수 데이터 반환
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Query", description = "내 정보 및 사용자 정보를 조회합니다.")
public class UserQueryController {

    private final UserQueryService userQueryService;

    private final UserFacade userFacade;

    @GetMapping("/me")
    @Operation(
            summary = "내 정보 조회",
            description = "Gateway가 JWT에서 추출해 전달한 X-User-Id를 이용해 로그인 사용자의 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "X-User-Id가 올바른 UUID 형식이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<UserResponseDto> getMyInfo(
            @Parameter(
                    description = "Gateway가 JWT의 subject에서 추출한 사용자 UUID. 로컬에서 직접 호출할 때는 이 값을 입력합니다.",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @RequestHeader("X-User-Id") String userId
    ) {
        UserResponseDto responseDto = userFacade.getUserById(UUID.fromString(userId));
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 단건 조회", description = "사용자 UUID로 사용자 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용자 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "userId가 올바른 UUID 형식이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<UserResponseDto> getUserById(
            @Parameter(
                    description = "조회할 사용자 UUID",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @PathVariable UUID userId
    ) {
        UserResponseDto responseDto = userFacade.getUserById(userId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 회원 목록 동적 조회 (QueryDSL + 공통 PageResponse 반환)
     */
    @GetMapping
    @Operation(
            summary = "사용자 목록 조회",
            description = "이메일·닉네임·권한·상태 조건으로 사용자를 검색하고 페이징된 순수 데이터를 반환합니다. 검색 조건은 모두 선택 사항입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용자 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "검색 조건 또는 페이징 값이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @Parameters({
            @Parameter(
                    name = "page",
                    description = "페이지 번호(0부터 시작)",
                    in = ParameterIn.QUERY,
                    schema = @Schema(implementation = Integer.class, defaultValue = "0", minimum = "0")
            ),
            @Parameter(
                    name = "size",
                    description = "한 페이지에 조회할 사용자 수",
                    in = ParameterIn.QUERY,
                    schema = @Schema(implementation = Integer.class, defaultValue = "20", minimum = "1")
            ),
            @Parameter(
                    name = "sort",
                    description = "정렬 기준과 방향. 예: createdAt,desc",
                    in = ParameterIn.QUERY,
                    example = "createdAt,desc"
            )
    })
    public ResponseEntity<PageResponse<UserResponseDto>> getUsers(
            @ParameterObject @ModelAttribute UserSearchCondition condition,
            @Parameter(hidden = true) Pageable pageable) {
        PageResponse<UserResponseDto> response = userFacade.getUsers(condition, pageable);
        return ResponseEntity.ok(response);
    }

}
