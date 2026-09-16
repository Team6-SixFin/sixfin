package com.sparta.learning.application.dto.result;

import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 피드백 목록 한 행에 필요한 컬럼만 담는 조회 결과 */
public record FeedbackListRow(
        Long feedbackId,
        UUID positionId,
        FeedbackType feedbackType,
        FeedbackStatus status,
        String summary,
        boolean aiUsed,
        UUID basedOnExecutionId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
