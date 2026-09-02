package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;

import java.util.List;
import java.util.UUID;

public record PositionFeedbackResponse(
        UUID positionId,
        String stockSymbol,
        String stockName,
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
