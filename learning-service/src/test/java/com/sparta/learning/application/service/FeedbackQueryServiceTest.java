package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.response.PageResponse;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 피드백 목록의 페이징, 응답 변환, 종목 정보 일괄 조회를 검증
 */
@ExtendWith(MockitoExtension.class)
class FeedbackQueryServiceTest {

    private static final UUID USER_ID = UUID.fromString("a8f2f9b7-f09a-4d51-a6ef-76de5c03b8f1");
    private static final UUID POSITION_ID = UUID.fromString("f4802bf4-b752-4d1f-9d3e-1f0a7ca57282");

    @Mock
    private FeedbackQueryRepository feedbackQueryRepository;

    @Mock
    private ExecutionSnapshotRepository executionSnapshotRepository;

    private FeedbackQueryService feedbackQueryService;

    @BeforeEach
    void setUp() {
        feedbackQueryService = new FeedbackQueryService(
                feedbackQueryRepository,
                executionSnapshotRepository
        );
    }

    // 최신순 페이지 조회 결과에 JSONB 요약과 체결 스냅샷 종목 정보가 포함되는지 확인
    @Test
    void returnsPagedFeedbacksWithExecutionStockInfo() {
        Feedback feedback = createFeedback();
        ExecutionSnapshot executionSnapshot = mock(ExecutionSnapshot.class);
        when(executionSnapshot.getPositionId()).thenReturn(POSITION_ID);
        when(executionSnapshot.getStockSymbol()).thenReturn("AAPL");
        when(executionSnapshot.getStockName()).thenReturn("Apple Inc.");

        PageRequest requestedPage = PageRequest.of(0, 20);
        when(feedbackQueryRepository.findAllByQuery(any(FeedbackListQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedback), requestedPage, 1));
        when(executionSnapshotRepository.findByPositionIdInOrderByExecutedAtAsc(Set.of(POSITION_ID)))
                .thenReturn(List.of(executionSnapshot));

        FeedbackListQuery query = FeedbackListQuery.of(
                USER_ID,
                "ENTRY_FEEDBACK",
                POSITION_ID,
                "COMPLETED",
                0,
                20
        );
        PageResponse<FeedbackListItemResponse> result = feedbackQueryService.getFeedbacks(query);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().stockSymbol()).isEqualTo("AAPL");
        assertThat(result.content().getFirst().stockName()).isEqualTo("Apple Inc.");
        assertThat(result.content().getFirst().summary()).isEqualTo("손절 계획을 설정했습니다.");
        assertThat(result.totalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedbackQueryRepository).findAllByQuery(eq(query), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    // 피드백보다 먼저 저장되어야 할 체결 스냅샷이 누락되면 종목 정보를 임의로 보완하지 않는다
    @Test
    void returnsNullStockInfoWhenExecutionSnapshotIsMissing() {
        Feedback feedback = createFeedback();

        when(feedbackQueryRepository.findAllByQuery(any(FeedbackListQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedback), PageRequest.of(0, 20), 1));
        when(executionSnapshotRepository.findByPositionIdInOrderByExecutedAtAsc(Set.of(POSITION_ID)))
                .thenReturn(List.of());

        PageResponse<FeedbackListItemResponse> result = feedbackQueryService.getFeedbacks(
                FeedbackListQuery.of(USER_ID, null, null, null, 0, 20)
        );

        assertThat(result.content().getFirst().stockSymbol()).isNull();
        assertThat(result.content().getFirst().stockName()).isNull();
    }

    // 빈 페이지에서는 불필요한 스냅샷 조회를 실행하지 않는지 확인
    @Test
    void skipsSnapshotQueriesForEmptyPage() {
        when(feedbackQueryRepository.findAllByQuery(any(FeedbackListQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<FeedbackListItemResponse> result = feedbackQueryService.getFeedbacks(
                FeedbackListQuery.of(USER_ID, null, null, null, 0, 20)
        );

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(executionSnapshotRepository);
    }

    private Feedback createFeedback() {
        Feedback feedback = mock(Feedback.class);
        when(feedback.getId()).thenReturn(101L);
        when(feedback.getPositionId()).thenReturn(POSITION_ID);
        when(feedback.getFeedbackType()).thenReturn(FeedbackType.ENTRY_FEEDBACK);
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedback.getContent()).thenReturn(
                JsonNodeFactory.instance.objectNode().put("summary", "손절 계획을 설정했습니다.")
        );
        when(feedback.isAiUsed()).thenReturn(true);
        when(feedback.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-08-31T10:00:00+09:00"));
        when(feedback.getCompletedAt()).thenReturn(OffsetDateTime.parse("2026-08-31T10:00:05+09:00"));
        return feedback;
    }
}
