package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.response.PositionFeedbackItemResponse;
import com.sparta.learning.application.dto.response.PositionFeedbackResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.GlobalExceptionHandler;
import com.sparta.learning.global.exception.LearningErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/positions/{positionId}/feedbacks의 경로 전달과 순수 데이터 응답을 검증
 */
@ExtendWith(MockitoExtension.class)
class PositionFeedbackQueryControllerTest {

    private static final UUID USER_ID = UUID.fromString("a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1");
    private static final UUID POSITION_ID = UUID.fromString("f4802bf4-b752-4d1f-9d3e-1f0a7ca57282");

    @Mock
    private FeedbackQueryService feedbackQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PositionFeedbackQueryController(feedbackQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // 최초 매수부터 종료 회고까지 피드백 배열을 wrapping 없이 반환하는지 확인
    @Test
    void getsPositionFeedbacks() throws Exception {
        PositionFeedbackItemResponse item = new PositionFeedbackItemResponse(
                101L,
                FeedbackType.ENTRY_FEEDBACK,
                FeedbackStatus.COMPLETED,
                "최초 매수 피드백",
                true,
                null,
                OffsetDateTime.parse("2026-08-31T10:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T10:00:05+09:00")
        );
        PositionFeedbackResponse response = new PositionFeedbackResponse(
                POSITION_ID,
                "AAPL",
                "Apple Inc.",
                List.of(item)
        );
        when(feedbackQueryService.getPositionFeedbacks(USER_ID, POSITION_ID)).thenReturn(response);

        mockMvc.perform(get("/api/positions/{positionId}/feedbacks", POSITION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId").value(POSITION_ID.toString()))
                .andExpect(jsonPath("$.stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(101))
                .andExpect(jsonPath("$.feedbacks[0].summary").value("최초 매수 피드백"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(feedbackQueryService).getPositionFeedbacks(USER_ID, POSITION_ID);
    }

    // 사용자에게 속하지 않은 포지션을 같은 404 오류로 응답하는지 확인
    @Test
    void rejectsMissingOrUnownedPosition() throws Exception {
        when(feedbackQueryService.getPositionFeedbacks(USER_ID, POSITION_ID))
                .thenThrow(new CustomException(LearningErrorCode.POSITION_NOT_FOUND));

        mockMvc.perform(get("/api/positions/{positionId}/feedbacks", POSITION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSITION_NOT_FOUND"));
    }
}
