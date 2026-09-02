package com.sparta.learning.application.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FeedbackDiagnosisResponse(
        Long diagnosisId,
        String ruleCode,
        DiagnosisStatus result,
        String message,
        Map<String, Object> metrics,
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
