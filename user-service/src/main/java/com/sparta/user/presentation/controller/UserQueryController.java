package com.sparta.user.presentation.controller;

import com.sparta.user.application.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//search 같이 DB에 변동 사항이 없는 경우 사용하시면 됩니다.

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
