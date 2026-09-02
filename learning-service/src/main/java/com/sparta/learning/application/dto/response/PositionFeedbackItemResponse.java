package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PositionFeedbackItemResponse(
        Long feedbackId,
        FeedbackType feedbackType,
        FeedbackStatus status,
        String summary,
        boolean aiUsed,
        UUID basedOnExecutionId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {

    public static PositionFeedbackItemResponse from(Feedback feedback) {
        return new PositionFeedbackItemResponse(
                feedback.getId(),
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
