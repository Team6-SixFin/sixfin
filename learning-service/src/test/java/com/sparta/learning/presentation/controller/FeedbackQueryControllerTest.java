package com.sparta.learning.presentation.controller;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.application.service.FeedbackQueryService;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.GlobalExceptionHandler;
import com.sparta.learning.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/feedbacks의 요청 파라미터와 순수 데이터 응답 형식을 검증
 */
@ExtendWith(MockitoExtension.class)
class FeedbackQueryControllerTest {

    private static final UUID USER_ID = UUID.fromString("a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1");
    private static final UUID POSITION_ID = UUID.fromString("f4802bf4-b752-4d1f-9d3e-1f0a7ca57282");

    @Mock
    private FeedbackQueryService feedbackQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedbackQueryController(feedbackQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // Gateway가 전달한 사용자 ID와 필터를 Query로 변환하고 명세 형식으로 응답하는지 확인
    @Test
    void getsFeedbackList() throws Exception {
        FeedbackListItemResponse item = new FeedbackListItemResponse(
                101L,
                POSITION_ID,
                "AAPL",
                "Apple Inc.",
                FeedbackType.ENTRY_FEEDBACK,
                FeedbackStatus.COMPLETED,
                "손절 계획을 설정했습니다.",
                true,
                null,
                OffsetDateTime.parse("2026-08-31T10:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T10:00:05+09:00")
        );
        when(feedbackQueryService.getFeedbacks(any(FeedbackListQuery.class)))
                .thenReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/feedbacks")
                        .header("X-User-Id", USER_ID)
                        .param("type", "ENTRY_FEEDBACK")
                        .param("positionId", POSITION_ID.toString())
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].feedbackId").value(101))
                .andExpect(jsonPath("$.content[0].stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$.content[0].summary").value("손절 계획을 설정했습니다."))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<FeedbackListQuery> queryCaptor = ArgumentCaptor.forClass(FeedbackListQuery.class);
        verify(feedbackQueryService).getFeedbacks(queryCaptor.capture());
        assertThat(queryCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(queryCaptor.getValue().feedbackType()).isEqualTo(FeedbackType.ENTRY_FEEDBACK);
        assertThat(queryCaptor.getValue().positionId()).isEqualTo(POSITION_ID);
        assertThat(queryCaptor.getValue().status()).isEqualTo(FeedbackStatus.COMPLETED);
    }

    // 현재 지원하지 않는 피드백 상태는 서비스 호출 전에 400 응답으로 변환되는지 확인
    @Test
    void rejectsUnsupportedFeedbackStatus() throws Exception {
        mockMvc.perform(get("/api/feedbacks")
                        .header("X-User-Id", USER_ID)
                        .param("status", "PROCESSING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATUS"));
    }
}
