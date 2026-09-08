package com.sparta.learning.application.dto.query;

import com.sparta.learning.domain.model.FailedEventStatus;
import com.sparta.learning.domain.model.TradeEventType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

/*
 * 실패 이벤트 목록의 검색 조건
 * 문자열 -> Enum으로 변환해 Repository 계층에 검증이 끝난 값만 전달
 */
public record FailedEventListQuery(
        FailedEventStatus status,
        TradeEventType eventType,
        UUID userId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {

    private static final int MAX_PAGE_SIZE = 100;

    public static FailedEventListQuery of(
            String status,
            String eventType,
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(LearningErrorCode.INVALID_PAGE_REQUEST);
        }

        // from이나 to 한쪽만 지정해도 조회할 수 있어 둘 다 있을 때만 순서를 확인한다
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(LearningErrorCode.INVALID_DATE_RANGE);
        }

        return new FailedEventListQuery(
                parseStatus(status),
                parseEventType(eventType),
                userId,
                from,
                to,
                page,
                size
        );
    }

    private static FailedEventStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return FailedEventStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(LearningErrorCode.INVALID_FAILED_EVENT_STATUS);
        }
    }

    private static TradeEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }

        try {
            return TradeEventType.valueOf(eventType.trim());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(LearningErrorCode.INVALID_EVENT_TYPE);
        }
    }
}
