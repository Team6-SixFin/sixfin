package com.sparta.learning.application.dto.query;

import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 피드백 목록 Query Parameter가 조회 조건으로 올바르게 변환되는지 검증
 */
class FeedbackListQueryTest {

    private static final UUID USER_ID = UUID.fromString("a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1");
    private static final UUID POSITION_ID = UUID.fromString("f4802bf4-b752-4d1f-9d3e-1f0a7ca57282");

    // API 문자열 필터를 실제 Entity에서 사용하는 Enum으로 변환하는지 확인
    @Test
    void parsesFeedbackFilters() {
        FeedbackListQuery query = FeedbackListQuery.of(
                USER_ID,
                "ENTRY_FEEDBACK",
                POSITION_ID,
                "COMPLETED",
                0,
                20
        );

        assertThat(query.userId()).isEqualTo(USER_ID);
        assertThat(query.feedbackType()).isEqualTo(FeedbackType.ENTRY_FEEDBACK);
        assertThat(query.positionId()).isEqualTo(POSITION_ID);
        assertThat(query.status()).isEqualTo(FeedbackStatus.COMPLETED);
    }

    // 존재하지 않는 PROCESSING 상태는 현재 FeedbackStatus 계약에 없으므로 400 대상이 되는지 확인
    @Test
    void rejectsUnsupportedFeedbackStatus() {
        assertThatThrownBy(() -> FeedbackListQuery.of(USER_ID, null, null, "PROCESSING", 0, 20))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(LearningErrorCode.INVALID_FEEDBACK_STATUS);
    }

    // 과도한 조회를 막기 위해 페이지 크기가 1~100 범위를 벗어나면 거부하는지 확인
    @Test
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> FeedbackListQuery.of(USER_ID, null, null, null, 0, 101))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(LearningErrorCode.INVALID_PAGE_REQUEST);
    }
}
