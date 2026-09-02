package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record FeedbackDetailResponse(
        Long feedbackId,
        UUID positionId,
        String stockSymbol,
        String stockName,
        FeedbackType feedbackType,
        FeedbackStatus status,
        Map<String, Object> feedbackContent,
        List<FeedbackDiagnosisResponse> diagnoses,
        List<FeedbackEvidenceResponse> evidences,
        List<FeedbackResourceResponse> resources,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {

    public static FeedbackDetailResponse from(
            Feedback feedback,
            ExecutionSnapshot firstExecution,
            List<DiagnosisResult> diagnosisResults,
            List<FeedbackResource> feedbackResources
    ) {
        List<FeedbackDiagnosisResponse> diagnoses = diagnosisResults.stream()
                .map(FeedbackDiagnosisResponse::from)
                .toList();

        // 여러 진단이 같은 체결을 근거로 사용할 수 있으므로 executionId를 기준으로 중복을 제거
        LinkedHashMap<UUID, ExecutionSnapshot> evidenceByExecutionId = new LinkedHashMap<>();
        diagnosisResults.stream()
                .map(DiagnosisResult::getExecutionSnapshot)
                .filter(Objects::nonNull)
                .forEach(snapshot -> evidenceByExecutionId.putIfAbsent(snapshot.getExecutionId(), snapshot));

        List<FeedbackEvidenceResponse> evidences = evidenceByExecutionId.values().stream()
                .map(FeedbackEvidenceResponse::from)
                .toList();

        List<FeedbackResourceResponse> resources = feedbackResources.stream()
                .map(FeedbackResourceResponse::from)
                .toList();

        return new FeedbackDetailResponse(
                feedback.getId(),
                feedback.getPositionId(),
                firstExecution == null ? null : firstExecution.getStockSymbol(),
                firstExecution == null ? null : firstExecution.getStockName(),
                feedback.getFeedbackType(),
                feedback.getStatus(),
                JsonResponseMapper.toMap(feedback.getContent()),
                diagnoses,
                evidences,
                resources,
                feedback.getCreatedAt(),
                feedback.getCompletedAt()
        );
    }
}
