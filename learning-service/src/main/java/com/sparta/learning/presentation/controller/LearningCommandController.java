package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.service.LearningCommandService;
import com.sparta.learning.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 피드백 생성 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Feedback Command", description = "현재 포지션의 체결 및 진단 데이터를 이용해 AI 피드백을 생성합니다.")
public class LearningCommandController {

    private final LearningCommandService learningCommandService;

    // 요청형 피드백 생성 API
    @PostMapping("/positions/{positionId}/feedbacks")
    @Operation(
            summary = "요청형 매매 피드백 생성",
            description = "현재까지 저장된 체결 스냅샷과 ENTRY·TRADE 진단 결과를 Gemini에 전달해 요청형 피드백을 생성합니다. "
                    + "AI 호출이 끝날 때까지 기다리는 동기 API이며 Request Body는 사용하지 않습니다. "
                    + "같은 포지션과 최신 체결 ID로 완료된 피드백이 있으면 AI를 다시 호출하지 않고 기존 결과를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청형 피드백 생성 또는 기존 결과 반환 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "포지션의 최신 체결을 찾을 수 없거나 AI 피드백 생성에 실패함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public AiFeedbackResponse requestOnDemandFeedback(
            @Parameter(
                    description = "Trading 서비스가 발급한 포지션 UUID",
                    required = true,
                    example = "f4802bf4-b752-4d1f-9d3e-1f0a7ca57282"
            )
            @PathVariable UUID positionId,
            @Parameter(
                    description = "Gateway가 JWT의 subject에서 추출한 사용자 UUID. 로컬에서 직접 호출할 때는 이 값을 입력합니다.",
                    required = true,
                    example = "a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1"
            )
            @RequestHeader("X-User-Id") UUID userId
    ) {
        // 요청된 positionId에 대해 피드백 생성 프로세스 시작
        AiFeedbackResponse response = learningCommandService.createOnDemandFeedback(positionId, userId);

        // 클라이언트에게 피드백 내용(제목, 본문, 조언)을 담아 응답
        return response;
    }
}
