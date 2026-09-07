package com.sparta.learning.application.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "피드백 생성에 사용된 규칙 기반 진단 결과")
public record FeedbackDiagnosisResponse(
        @Schema(description = "Learning 진단 결과 ID", example = "301")
        Long diagnosisId,

        @Schema(
                description = "적용한 진단 규칙 코드",
                example = "HIGH_CHASING_BUY",
                allowableValues = {
                        "STOP_LOSS_SET", "STOP_LOSS_WIDTH", "HIGH_CHASING_BUY", "SHORT_TERM_SURGE_BUY",
                        "REPEATED_HIGH_CHASING_BUY", "SELL_BELOW_STOP_LOSS",
                        "STOP_LOSS_ADHERENCE", "HIGH_CHASING_FREQUENCY"
                }
        )
        String ruleCode,

        @Schema(description = "규칙 판정 결과", example = "WARNING")
        DiagnosisStatus result,

        @Schema(description = "사용자가 이해할 수 있는 판정 근거 문장", example = "최근 20일 최고가에 가까운 가격에서 매수했습니다.")
        String message,

        @Schema(
                description = "판정 계산에 사용한 값. 규칙에 따라 키 구성이 달라집니다.",
                example = "{\"executedPrice\":183.17,\"recent20dHigh\":184.74,\"highPriceRatio\":99.15}"
        )
        Map<String, Object> metrics,

        @Schema(description = "이 진단의 근거가 된 체결 UUID 목록", example = "[\"0be48fa2-01f6-4d82-8c30-6bc9f2d51e10\"]")
        List<UUID> evidenceExecutionIds
) {

    public static FeedbackDiagnosisResponse from(DiagnosisResult diagnosis) {
        ExecutionSnapshot executionSnapshot = diagnosis.getExecutionSnapshot();
        List<UUID> evidenceExecutionIds = executionSnapshot == null
                ? List.of()
                : List.of(executionSnapshot.getExecutionId());

        return new FeedbackDiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getRuleCode(),
                diagnosis.getResult(),
                extractMessage(diagnosis.getEvidence()),
                JsonResponseMapper.toMap(diagnosis.getMetrics()),
                evidenceExecutionIds
        );
    }

    private static String extractMessage(JsonNode evidence) {
        if (evidence == null) {
            return null;
        }

        JsonNode message = evidence.get("message");
        return message != null && message.isTextual() ? message.textValue() : null;
    }
}
