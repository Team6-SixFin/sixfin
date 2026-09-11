package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.query.FailedEventListQuery;
import com.sparta.learning.application.dto.response.FailedEventListItemResponse;
import com.sparta.learning.application.dto.response.FailedEventRetryResponse;
import com.sparta.learning.application.service.FailedEventAdminService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/* 재시도를 모두 소진해 DLT로 보관된 이벤트를 조회하고 재처리한다. */
@RestController
@RequestMapping("/api/learnings/admin/events/failed")
@RequiredArgsConstructor
@Tag(name = "Failed Event Admin", description = "Kafka 소비에 실패한 이벤트를 조회하고 재처리합니다.")
public class FailedEventAdminController {

    private final FailedEventAdminService failedEventAdminService;

    @GetMapping
    @Operation(
            summary = "실패 이벤트 목록 조회",
            description = "DLT로 보관된 이벤트를 최신순으로 조회합니다. "
                    + "상태/이벤트 종류/사용자/기간으로 필터링할 수 있고 페이지는 0부터 시작합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "실패 이벤트 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 필터 값이거나 페이지·기간 범위가 바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public PageResponse<FailedEventListItemResponse> getFailedEvents(
            @Parameter(description = "재처리 상태", example = "PENDING")
            @RequestParam(required = false) String status,

            @Parameter(description = "이벤트 종류", example = "BUY_EXECUTED")
            @RequestParam(required = false) String eventType,

            @Parameter(description = "이벤트 소유자 UUID")
            @RequestParam(required = false) UUID userId,

            @Parameter(description = "실패 기록 시작 시각", example = "2026-09-01T00:00:00+09:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @Parameter(description = "실패 기록 종료 시각", example = "2026-09-30T23:59:59+09:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,

            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return failedEventAdminService.getFailedEvents(
                FailedEventListQuery.of(status, eventType, userId, from, to, page, size)
        );
    }

    @PostMapping("/{id}/retry")
    @Operation(
            summary = "실패 이벤트 재처리",
            description = "보관된 원본으로 수집부터 다시 실행합니다. "
                    + "수집이 이미 끝난 이벤트는 중복으로 걸러지고 진단만 다시 실행되므로, "
                    + "수집 실패와 진단 실패를 같은 경로로 복구합니다. "
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재처리 성공"),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "실패 이벤트를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 재처리된 이벤트",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "보관된 원본을 복원할 수 없어 재처리를 시작하지 못함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "재처리 중 다시 실패함. 실패 사유가 갱신됩니다.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public FailedEventRetryResponse retry(
            @Parameter(description = "실패 이벤트 ID", required = true, example = "1024")
            @PathVariable Long id
    ) {
        return failedEventAdminService.retry(id);
    }
}
