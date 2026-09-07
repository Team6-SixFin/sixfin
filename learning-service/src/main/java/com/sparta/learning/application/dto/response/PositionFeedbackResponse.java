package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "하나의 포지션에서 생성된 전체 피드백 흐름")
public record PositionFeedbackResponse(
        @Schema(description = "Trading 서비스가 발급한 포지션 UUID", format = "uuid", example = "f4802bf4-b752-4d1f-9d3e-1f0a7ca57282")
        UUID positionId,

        @Schema(description = "미국 주식 티커", example = "AAPL")
        String stockSymbol,

        @Schema(description = "종목명", example = "Apple Inc.")
        String stockName,

        @Schema(description = "생성 시각 오름차순으로 정렬된 포지션의 피드백 목록")
        List<PositionFeedbackItemResponse> feedbacks
) {

    public static PositionFeedbackResponse from(
            ExecutionSnapshot firstExecution,
            List<Feedback> feedbacks
    ) {
        return new PositionFeedbackResponse(
                firstExecution.getPositionId(),
                firstExecution.getStockSymbol(),
                firstExecution.getStockName(),
                feedbacks.stream()
                        .map(PositionFeedbackItemResponse::from)
                        .toList()
        );
    }
}
