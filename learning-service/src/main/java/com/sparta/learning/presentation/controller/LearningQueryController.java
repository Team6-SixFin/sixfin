package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.service.LearningQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//search 같이 DB에 변동 사항이 없는 경우 사용하시면 됩니다.

@RestController
@RequestMapping("api/user")
@RequiredArgsConstructor
public class LearningQueryController {

    private final LearningQueryService learningQueryService;

/**
 * 작성자 :
 * 최초 작성일 :
 * 최종 수정일 :
 * 기능 :
 * 설명 :
 * @Param:
 **/

}
