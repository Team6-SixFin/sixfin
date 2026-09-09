package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackDetailResponse;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import com.sparta.learning.global.response.ErrorResponse;
import com.sparta.learning.global.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedback Query", description = "로그인 사용자의 AI 피드백 목록과 상세 결과를 조회합니다.")
public class FeedbackQueryController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final FeedbackQueryService feedbackQueryService;

    /**
     * 외부 클라이언트의 JWT는 Gateway가 검증하고, Learning에는 신뢰된 userId 헤더 전달
     */
    @GetMapping
    @Operation(
            summary = "피드백 목록 조회",
            description = "로그인 사용자의 최초 매수, 요청형, 포지션 종료 피드백을 최신순으로 조회합니다. "
                    + "피드백 종류·포지션·생성 상태로 필터링할 수 있으며 페이지 번호는 0부터 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "피드백 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 필터 값이거나 페이지 범위가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public PageResponse<FeedbackListItemResponse> getFeedbacks(
            @Parameter(
                    description = "Gateway가 JWT의 subject에서 추출한 사용자 UUID. 로컬에서 직접 호출할 때는 이 값을 입력합니다.",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @Parameter(
                    description = "피드백 생성 계기. 생략하면 모든 종류를 조회합니다.",
                    schema = @Schema(allowableValues = {"ENTRY_FEEDBACK", "ON_DEMAND_FEEDBACK", "POSITION_REVIEW"}),
                    example = "ON_DEMAND_FEEDBACK"
            )
            @RequestParam(required = false) String type,
            @Parameter(
                    description = "특정 Trading 포지션의 피드백만 조회할 때 사용하는 포지션 UUID",
                    example = "f4802bf4-b752-4d1f-9d3e-1f0a7ca57282"
            )
            @RequestParam(required = false) UUID positionId,
            @Parameter(
                    description = "피드백 생성 상태. 생략하면 모든 상태를 조회합니다.",
                    schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"}),
                    example = "COMPLETED"
            )
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0", schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(
                    description = "페이지당 피드백 수(1~100)",
                    example = "20",
                    schema = @Schema(minimum = "1", maximum = "100")
            )
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

    @GetMapping("/{feedbackId}")
    @Operation(
            summary = "피드백 상세 조회",
            description = "피드백 본문과 생성에 사용된 규칙 진단, 근거 체결, 추천 학습 자료를 함께 조회합니다. "
                    + "본인이 소유한 피드백만 조회할 수 있으며 존재하지 않거나 소유자가 다르면 동일하게 404를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "피드백 상세 조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "피드백이 존재하지 않거나 요청 사용자의 피드백이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public FeedbackDetailResponse getFeedbackDetail(
            @Parameter(
                    description = "Gateway가 JWT에서 추출한 사용자 UUID",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @Parameter(description = "Learning 서비스가 발급한 피드백 ID", required = true, example = "101")
            @PathVariable Long feedbackId
    ) {
        return feedbackQueryService.getFeedbackDetail(userId, feedbackId);
    }
}
