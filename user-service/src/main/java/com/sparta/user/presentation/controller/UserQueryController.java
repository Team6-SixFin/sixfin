package com.sparta.user.presentation.controller;

import com.sparta.user.application.dto.request.UserSearchCondition;
import com.sparta.user.application.dto.response.UserResponseDto;
import com.sparta.user.application.facade.UserFacade;
import com.sparta.user.application.service.UserQueryService;
import com.sparta.user.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
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
public class UserQueryController {

    private final UserQueryService userQueryService;

    private final UserFacade userFacade;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyInfo(@RequestHeader("X-User-Id") String userId) {
        UserResponseDto responseDto = userFacade.getUserById(UUID.fromString(userId));
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID userId) {
        UserResponseDto responseDto = userFacade.getUserById(userId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 회원 목록 동적 조회 (QueryDSL + 공통 PageResponse 반환)
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserResponseDto>> getUsers(
            @ModelAttribute UserSearchCondition condition,
            Pageable pageable) {
        PageResponse<UserResponseDto> response = userFacade.getUsers(condition, pageable);
        return ResponseEntity.ok(response);
    }

}
