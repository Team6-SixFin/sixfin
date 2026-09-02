package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.response.PositionFeedbackResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionFeedbackQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final FeedbackQueryService feedbackQueryService;

    @GetMapping("/{positionId}/feedbacks")
    public PositionFeedbackResponse getPositionFeedbacks(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @PathVariable UUID positionId
    ) {
        return feedbackQueryService.getPositionFeedbacks(userId, positionId);
    }
}
