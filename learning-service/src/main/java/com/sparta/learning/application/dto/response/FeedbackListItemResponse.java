package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "피드백 목록의 한 항목")
public record FeedbackListItemResponse(
        @Schema(description = "Learning 서비스가 발급한 피드백 ID", example = "101")
        Long feedbackId,

        @Schema(description = "Trading 서비스가 발급한 포지션 UUID", format = "uuid", example = "f4802bf4-b752-4d1f-9d3e-1f0a7ca57282")
        UUID positionId,

        @Schema(description = "미국 주식 티커", example = "AAPL")
        String stockSymbol,

        @Schema(description = "종목명", example = "Apple Inc.")
        String stockName,

        @Schema(description = "피드백 생성 계기", example = "ON_DEMAND_FEEDBACK")
        FeedbackType feedbackType,

        @Schema(description = "피드백 생성 상태", example = "COMPLETED")
        FeedbackStatus status,

        @Schema(description = "feedbacks.content의 summary에서 추출한 한 문장 요약", example = "손절 계획은 세웠지만 진입 시점에 주의가 필요합니다.")
        String summary,

        @Schema(description = "최종 피드백 생성에 AI를 사용했는지 여부", example = "true")
        boolean aiUsed,

        @Schema(description = "이 피드백에 마지막으로 반영된 체결 UUID", format = "uuid", example = "488ca691-334b-4187-ab3c-161630054ac5")
        UUID basedOnExecutionId,

        @Schema(description = "피드백 생성 요청 시각", format = "date-time", example = "2026-09-07T10:31:10+09:00")
        OffsetDateTime createdAt,

        @Schema(description = "피드백 생성 완료 또는 실패 시각. PENDING이면 null", format = "date-time", example = "2026-09-07T10:31:15+09:00")
        OffsetDateTime completedAt
) {

    public static FeedbackListItemResponse from(
            Feedback feedback,
            String stockSymbol,
            String stockName
    ) {
        return new FeedbackListItemResponse(
                feedback.getId(),
                feedback.getPositionId(),
                stockSymbol,
                stockName,
                feedback.getFeedbackType(),
                feedback.getStatus(),
                JsonResponseMapper.textValue(feedback.getContent(), "summary"),
                feedback.isAiUsed(),
                feedback.getBasedOnExecutionId(),
                feedback.getCreatedAt(),
                feedback.getCompletedAt()
        );
    }

}
