package com.sparta.learning.application.dto.query;

import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;

import java.util.UUID;

/**
 * 피드백 목록의 검색 조건
 * 문자열 -> Enum으로 변환해 Repository 계층에 검증이 끝난 값만 전달
 */
public record FeedbackListQuery(
        UUID userId,
        FeedbackType feedbackType,
        UUID positionId,
        FeedbackStatus status,
        int page,
        int size
) {

    private static final int MAX_PAGE_SIZE = 100;

    public static FeedbackListQuery of(
            UUID userId,
            String type,
            UUID positionId,
            String status,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(LearningErrorCode.INVALID_PAGE_REQUEST);
        }

        return new FeedbackListQuery(
                userId,
                parseType(type),
                positionId,
                parseStatus(status),
                page,
                size
        );
    }

    private static FeedbackType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        try {
            return FeedbackType.valueOf(type.trim());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(LearningErrorCode.INVALID_FEEDBACK_TYPE);
        }
    }

    private static FeedbackStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return FeedbackStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(LearningErrorCode.INVALID_FEEDBACK_STATUS);
        }
    }
}
