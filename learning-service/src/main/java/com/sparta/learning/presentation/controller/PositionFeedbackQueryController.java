package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.response.PositionFeedbackResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import com.sparta.learning.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Position Feedback Query", description = "특정 포지션에서 생성된 피드백 흐름을 조회합니다.")
public class PositionFeedbackQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final FeedbackQueryService feedbackQueryService;

    @GetMapping("/{positionId}/feedbacks")
    @Operation(
            summary = "포지션별 피드백 조회",
            description = "특정 포지션에서 생성된 최초 매수·요청형·포지션 종료 피드백을 생성 시각 오름차순으로 조회합니다. "
                    + "최초 체결 스냅샷을 이용해 포지션 소유권과 종목 정보를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "포지션별 피드백 조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "포지션이 존재하지 않거나 요청 사용자의 포지션이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public PositionFeedbackResponse getPositionFeedbacks(
            @Parameter(
                    description = "Gateway가 JWT에서 추출한 사용자 UUID",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @Parameter(
                    description = "Trading 서비스가 발급한 포지션 UUID",
                    required = true,
                    example = "f4802bf4-b752-4d1f-9d3e-1f0a7ca57282"
            )
            @PathVariable UUID positionId
    ) {
        return feedbackQueryService.getPositionFeedbacks(userId, positionId);
    }
}
