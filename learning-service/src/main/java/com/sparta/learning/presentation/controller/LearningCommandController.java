package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.service.LearningCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 피드백 생성 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LearningCommandController {

    private final LearningCommandService learningCommandService;

    // 요청형 피드백 생성 API
    @PostMapping("/positions/{positionId}/feedbacks")
    public AiFeedbackResponse requestOnDemandFeedback(
            @PathVariable UUID positionId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        // 요청된 positionId에 대해 피드백 생성 프로세스 시작
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // 클라이언트에게 피드백 내용(제목, 본문, 조언)을 담아 응답
        return response;
    }
}