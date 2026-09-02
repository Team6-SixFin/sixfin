package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackDetailResponse;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.application.dto.response.PositionFeedbackResponse;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.TradeType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.global.response.PageResponse;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackDetailQueryRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private FeedbackDetailQueryRepository feedbackDetailQueryRepository;

    private FeedbackQueryService feedbackQueryService;

    @BeforeEach
    void setUp() {
        feedbackQueryService = new FeedbackQueryService(
                feedbackQueryRepository,
                executionSnapshotRepository,
                feedbackDetailQueryRepository
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

    // 상세 조회 시 피드백과 연결된 진단, 근거 체결, 교육 자료를 하나의 응답으로 조립하는지 확인
    @Test
    void returnsFeedbackDetail() {
        Feedback feedback = createDetailFeedback();
        ExecutionSnapshot execution = createExecutionSnapshot();
        DiagnosisResult diagnosis = mock(DiagnosisResult.class);
        FeedbackResource feedbackResource = mock(FeedbackResource.class);
        LearningResource learningResource = mock(LearningResource.class);

        when(diagnosis.getId()).thenReturn(201L);
        when(diagnosis.getRuleCode()).thenReturn("HIGH_PRICE_CHASING");
        when(diagnosis.getResult()).thenReturn(DiagnosisStatus.WARNING);
        when(diagnosis.getMetrics()).thenReturn(
                JsonNodeFactory.instance.objectNode().put("highRatio", 99.29)
        );
        when(diagnosis.getEvidence()).thenReturn(
                JsonNodeFactory.instance.objectNode().put("message", "최근 고점 부근에서 매수했습니다.")
        );
        when(diagnosis.getExecutionSnapshot()).thenReturn(execution);

        when(feedbackResource.getLearningResource()).thenReturn(learningResource);
        when(feedbackResource.getRecommendationReason()).thenReturn("고점 추격 매수 습관을 점검하는 자료입니다.");
        when(learningResource.getId()).thenReturn(301L);
        when(learningResource.getTitle()).thenReturn("추격 매수를 피하는 방법");
        when(learningResource.getUrl()).thenReturn("https://youtube.com/watch?v=example");
        when(learningResource.getChannelName()).thenReturn("투자교육 채널");
        when(learningResource.getThumbnailUrl()).thenReturn("https://img.youtube.com/example.jpg");

        when(feedbackDetailQueryRepository.findFeedback(101L, USER_ID)).thenReturn(Optional.of(feedback));
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(
                POSITION_ID,
                USER_ID
        )).thenReturn(Optional.of(execution));
        when(feedbackDetailQueryRepository.findDiagnoses(101L)).thenReturn(List.of(diagnosis));
        when(feedbackDetailQueryRepository.findActiveResources(101L)).thenReturn(List.of(feedbackResource));

        FeedbackDetailResponse result = feedbackQueryService.getFeedbackDetail(USER_ID, 101L);

        assertThat(result.feedbackId()).isEqualTo(101L);
        assertThat(result.stockSymbol()).isEqualTo("AAPL");
        assertThat(result.feedbackContent().get("summary")).isEqualTo("손절 계획을 설정했습니다.");
        assertThat(result.diagnoses()).hasSize(1);
        assertThat(result.diagnoses().getFirst().message()).isEqualTo("최근 고점 부근에서 매수했습니다.");
        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().getFirst().executionId()).isEqualTo(execution.getExecutionId());
        assertThat(result.resources()).hasSize(1);
        assertThat(result.resources().getFirst().resourceType()).isEqualTo("VIDEO");
    }

    // 존재하지 않거나 다른 사용자의 피드백은 동일하게 404 오류로 처리하는지 확인
    @Test
    void rejectsMissingOrUnownedFeedback() {
        when(feedbackDetailQueryRepository.findFeedback(101L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackQueryService.getFeedbackDetail(USER_ID, 101L))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(LearningErrorCode.FEEDBACK_NOT_FOUND));
    }

    // 포지션 피드백을 시간 흐름대로 응답하고 최초 체결의 종목 정보를 사용하는지 확인
    @Test
    void returnsPositionFeedbackTimeline() {
        ExecutionSnapshot firstExecution = mock(ExecutionSnapshot.class);
        when(firstExecution.getPositionId()).thenReturn(POSITION_ID);
        when(firstExecution.getStockSymbol()).thenReturn("AAPL");
        when(firstExecution.getStockName()).thenReturn("Apple Inc.");

        Feedback entryFeedback = createPositionFeedback(
                101L,
                FeedbackType.ENTRY_FEEDBACK,
                "최초 매수 피드백",
                null,
                "2026-08-31T10:00:00+09:00"
        );
        UUID basedOnExecutionId = UUID.fromString("95214e93-2942-4c96-997f-ab34348ee019");
        Feedback onDemandFeedback = createPositionFeedback(
                102L,
                FeedbackType.ON_DEMAND_FEEDBACK,
                "요청형 피드백",
                basedOnExecutionId,
                "2026-08-31T11:00:00+09:00"
        );

        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(
                POSITION_ID,
                USER_ID
        )).thenReturn(Optional.of(firstExecution));
        when(feedbackQueryRepository.findAllByPosition(USER_ID, POSITION_ID))
                .thenReturn(List.of(entryFeedback, onDemandFeedback));

        PositionFeedbackResponse result = feedbackQueryService.getPositionFeedbacks(USER_ID, POSITION_ID);

        assertThat(result.positionId()).isEqualTo(POSITION_ID);
        assertThat(result.stockSymbol()).isEqualTo("AAPL");
        assertThat(result.feedbacks()).extracting(item -> item.feedbackId())
                .containsExactly(101L, 102L);
        assertThat(result.feedbacks().getFirst().summary()).isEqualTo("최초 매수 피드백");
        assertThat(result.feedbacks().get(1).basedOnExecutionId()).isEqualTo(basedOnExecutionId);
    }

    // 포지션은 존재하지만 피드백 생성 전이라면 정상 응답과 빈 배열을 반환
    @Test
    void returnsEmptyFeedbacksForKnownPosition() {
        ExecutionSnapshot firstExecution = mock(ExecutionSnapshot.class);
        when(firstExecution.getPositionId()).thenReturn(POSITION_ID);
        when(firstExecution.getStockSymbol()).thenReturn("AAPL");
        when(firstExecution.getStockName()).thenReturn("Apple Inc.");
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(
                POSITION_ID,
                USER_ID
        )).thenReturn(Optional.of(firstExecution));
        when(feedbackQueryRepository.findAllByPosition(USER_ID, POSITION_ID)).thenReturn(List.of());

        PositionFeedbackResponse result = feedbackQueryService.getPositionFeedbacks(USER_ID, POSITION_ID);

        assertThat(result.positionId()).isEqualTo(POSITION_ID);
        assertThat(result.feedbacks()).isEmpty();
    }

    // 존재하지 않거나 다른 사용자의 포지션은 피드백 목록을 조회하지 않고 404로 처리
    @Test
    void rejectsMissingOrUnownedPosition() {
        when(executionSnapshotRepository.findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(
                POSITION_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackQueryService.getPositionFeedbacks(USER_ID, POSITION_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(LearningErrorCode.POSITION_NOT_FOUND));
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

    private Feedback createDetailFeedback() {
        Feedback feedback = mock(Feedback.class);
        when(feedback.getId()).thenReturn(101L);
        when(feedback.getPositionId()).thenReturn(POSITION_ID);
        when(feedback.getFeedbackType()).thenReturn(FeedbackType.ENTRY_FEEDBACK);
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedback.getContent()).thenReturn(
                JsonNodeFactory.instance.objectNode().put("summary", "손절 계획을 설정했습니다.")
        );
        when(feedback.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-08-31T10:00:00+09:00"));
        when(feedback.getCompletedAt()).thenReturn(OffsetDateTime.parse("2026-08-31T10:00:05+09:00"));
        return feedback;
    }

    private ExecutionSnapshot createExecutionSnapshot() {
        ExecutionSnapshot execution = mock(ExecutionSnapshot.class);
        when(execution.getExecutionId()).thenReturn(
                UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")
        );
        when(execution.getStockSymbol()).thenReturn("AAPL");
        when(execution.getStockName()).thenReturn("Apple Inc.");
        when(execution.getTradeType()).thenReturn(TradeType.BUY);
        when(execution.getQuantity()).thenReturn(10);
        when(execution.getExecutedPrice()).thenReturn(new java.math.BigDecimal("183.1700"));
        when(execution.getPlannedStopLossPrice()).thenReturn(new java.math.BigDecimal("175.0000"));
        when(execution.getExecutedAt()).thenReturn(OffsetDateTime.parse("2026-08-31T10:00:00+09:00"));
        return execution;
    }

    private Feedback createPositionFeedback(
            Long feedbackId,
            FeedbackType feedbackType,
            String summary,
            UUID basedOnExecutionId,
            String createdAt
    ) {
        Feedback feedback = mock(Feedback.class);
        when(feedback.getId()).thenReturn(feedbackId);
        when(feedback.getFeedbackType()).thenReturn(feedbackType);
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedback.getContent()).thenReturn(
                JsonNodeFactory.instance.objectNode().put("summary", summary)
        );
        when(feedback.isAiUsed()).thenReturn(true);
        when(feedback.getBasedOnExecutionId()).thenReturn(basedOnExecutionId);
        when(feedback.getCreatedAt()).thenReturn(OffsetDateTime.parse(createdAt));
        when(feedback.getCompletedAt()).thenReturn(OffsetDateTime.parse(createdAt).plusSeconds(5));
        return feedback;
    }
}
