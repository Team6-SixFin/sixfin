package com.sparta.user.presentation.controller;

import com.sparta.user.application.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 사용자 정보 조회 REST Controller (/api/users)
 * - 성공 응답 Wrapping 제거 및 HTTP Status + 순수 데이터 반환
 */
@RestController
@RequestMapping("api/user")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryService userQueryService;

/**
 * 작성자 :
 * 최초 작성일 :
 * 최종 수정일 :
 * 기능 :
 * 설명 :
 * @Param:
 **/

}
