package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FeedbackEvidenceResponse(
        UUID executionId,
        TradeType tradeType,
        int quantity,
        BigDecimal executedPrice,
        BigDecimal stopLossPrice,
        OffsetDateTime executedAt
) {

    public static FeedbackEvidenceResponse from(ExecutionSnapshot snapshot) {
        return new FeedbackEvidenceResponse(
                snapshot.getExecutionId(),
                snapshot.getTradeType(),
                snapshot.getQuantity(),
                snapshot.getExecutedPrice(),
                snapshot.getPlannedStopLossPrice(),
                snapshot.getExecutedAt()
        );
    }
}
