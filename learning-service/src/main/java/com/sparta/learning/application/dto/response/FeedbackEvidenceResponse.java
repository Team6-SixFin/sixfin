package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "진단 결과의 근거가 된 개별 체결")
public record FeedbackEvidenceResponse(
        @Schema(description = "Trading 서비스가 발급한 체결 UUID", format = "uuid", example = "0be48fa2-01f6-4d82-8c30-6bc9f2d51e10")
        UUID executionId,

        @Schema(description = "매수·매도 구분", example = "BUY")
        TradeType tradeType,

        @Schema(description = "체결 수량", example = "10")
        int quantity,

        @Schema(description = "체결 가격(미국 달러)", example = "183.1700")
        BigDecimal executedPrice,

        @Schema(description = "체결 당시 계획 손절가. 설정하지 않았다면 null", example = "174.0000")
        BigDecimal stopLossPrice,

        @Schema(description = "체결 시각", format = "date-time", example = "2026-08-27T10:31:00-04:00")
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
