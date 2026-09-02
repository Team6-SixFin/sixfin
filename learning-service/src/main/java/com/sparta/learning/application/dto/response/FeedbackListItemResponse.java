package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FeedbackListItemResponse(
        Long feedbackId,
        UUID positionId,
        String stockSymbol,
        String stockName,
        FeedbackType feedbackType,
        FeedbackStatus status,
        String summary,
        boolean aiUsed,
        UUID basedOnExecutionId,
        OffsetDateTime createdAt,
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
