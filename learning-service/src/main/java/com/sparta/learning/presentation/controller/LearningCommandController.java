package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.service.LearningCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 피드백 생성 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LearningCommandController {

    private final LearningCommandService learningCommandService;

    // 요청형 피드백 생성 API
    @PostMapping("/positions/{positionId}/feedbacks")
    public ResponseEntity<String> requestOnDemandFeedback(
            @PathVariable String positionId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        // 요청된 positionId에 대해 피드백 생성 프로세스 시작
        learningCommandService.createOnDemandFeedback(positionId, userId);

        return ResponseEntity.ok("피드백 생성이 성공적으로 완료되었습니다.");
    }
}