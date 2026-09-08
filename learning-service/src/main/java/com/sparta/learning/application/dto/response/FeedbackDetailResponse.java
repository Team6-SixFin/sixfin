package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "AI 피드백 본문과 판정 근거를 포함한 상세 조회 결과")
public record FeedbackDetailResponse(
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

        @Schema(
                description = "AI가 생성한 피드백 본문. summary, overview, strengths, improvements, nextActions, reflectionQuestions로 구성됩니다.",
                implementation = AiFeedbackResponse.class
        )
        Map<String, Object> feedbackContent,

        @Schema(description = "이 피드백을 생성할 때 사용한 규칙 기반 진단 결과")
        List<FeedbackDiagnosisResponse> diagnoses,

        @Schema(description = "진단 근거가 된 체결 목록. 여러 진단이 같은 체결을 참조하면 executionId 기준으로 한 번만 반환합니다.")
        List<FeedbackEvidenceResponse> evidences,

        @Schema(description = "피드백에 연결된 활성 학습 자료. 학습 콘텐츠가 연결되지 않았다면 빈 배열입니다.")
        List<FeedbackResourceResponse> resources,

        @Schema(description = "피드백 생성 요청 시각", format = "date-time", example = "2026-09-07T10:31:10+09:00")
        OffsetDateTime createdAt,

        @Schema(description = "피드백 생성 완료 또는 실패 시각", format = "date-time", example = "2026-09-07T10:31:15+09:00")
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
