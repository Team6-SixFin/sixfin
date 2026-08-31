package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import com.sparta.learning.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final FeedbackQueryService feedbackQueryService;

    /**
     * 외부 클라이언트의 JWT는 Gateway가 검증하고, Learning에는 신뢰된 userId 헤더 전달
     */
    @GetMapping
    public PageResponse<FeedbackListItemResponse> getFeedbacks(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        FeedbackListQuery query = FeedbackListQuery.of(
                userId,
                type,
                positionId,
                status,
                page,
                size
        );

        return feedbackQueryService.getFeedbacks(query);
    }
}
