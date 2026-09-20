package com.sparta.learning.infrastructure.scheduler;

import com.sparta.learning.application.service.LearningCommandService;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.config.FeedbackRetryProperties;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 재처리 스케줄러가 밀린 피드백을 조금씩 빼내는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class FailedFeedbackRetrySchedulerTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private LearningCommandService learningCommandService;
    @Mock private TransactionTemplate transactionTemplate;

    private FeedbackRetryProperties properties;
    private FailedFeedbackRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new FeedbackRetryProperties();
        scheduler = new FailedFeedbackRetryScheduler(
                feedbackRepository, learningCommandService, properties, transactionTemplate);

        // 트랜잭션 경계는 검증 대상이 아니므로 그대로 통과시킨다.
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private Feedback feedback(FeedbackType feedbackType) {
        return Feedback.builder()
                .feedbackKey(feedbackType.name() + ":" + UUID.randomUUID())
                .userId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .basedOnExecutionId(UUID.randomUUID())
                .feedbackType(feedbackType)
                .build();
    }

    // 요청형은 기준 체결이 최신 체결이라 추가 매매가 들어오면 feedbackKey가 바뀐다.
    // 대상에 넣으면 매 주기마다 원래 행은 남겨둔 채 엉뚱한 피드백을 새로 만든다.
    @Test
    void 카프카로_시작된_두_타입만_재처리_대상으로_조회한다() {
        // given
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        // when
        scheduler.retryFailedFeedbacks();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedbackType>> typesCaptor = ArgumentCaptor.forClass(List.class);
        verify(feedbackRepository).findRetryTargets(
                typesCaptor.capture(), any(), anyInt(), any(), any());

        assertEquals(List.of(FeedbackType.ENTRY_FEEDBACK, FeedbackType.POSITION_REVIEW),
                typesCaptor.getValue());
    }

    // 밀린 건을 한꺼번에 밀어넣으면 executor를 다시 포화시켜 재시도가 또 거부된다.
    @Test
    void 한_주기에_설정한_건수만큼만_조회한다() {
        // given
        properties.setBatchSize(3);
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        // when
        scheduler.retryFailedFeedbacks();

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedbackRepository).findRetryTargets(
                any(), any(), anyInt(), any(), pageableCaptor.capture());

        assertEquals(3, pageableCaptor.getValue().getPageSize());
    }

    // ENTRY와 POSITION_REVIEW는 기준 체결과 컨텍스트가 달라 생성 경로가 나뉜다.
    @Test
    void 피드백_타입에_맞는_생성_경로로_넘긴다() {
        // given
        Feedback entry = feedback(FeedbackType.ENTRY_FEEDBACK);
        Feedback review = feedback(FeedbackType.POSITION_REVIEW);
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of(entry, review));
        when(learningCommandService.createEntryFeedback(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(learningCommandService.createPositionReviewFeedback(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        scheduler.retryFailedFeedbacks();

        // then
        verify(learningCommandService).createEntryFeedback(entry.getPositionId(), entry.getUserId());
        verify(learningCommandService).createPositionReviewFeedback(review.getPositionId(), review.getUserId());
    }

    // 한 건의 실패가 배치를 중단시키면 뒤 피드백들이 계속 밀린다.
    @Test
    void 한_건이_실패해도_남은_건을_계속_처리한다() {
        // given
        Feedback broken = feedback(FeedbackType.ENTRY_FEEDBACK);
        Feedback healthy = feedback(FeedbackType.ENTRY_FEEDBACK);
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of(broken, healthy));
        when(learningCommandService.createEntryFeedback(broken.getPositionId(), broken.getUserId()))
                .thenThrow(new IllegalStateException("기준 체결을 찾을 수 없습니다"));
        when(learningCommandService.createEntryFeedback(healthy.getPositionId(), healthy.getUserId()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        scheduler.retryFailedFeedbacks();

        // then
        verify(learningCommandService).createEntryFeedback(healthy.getPositionId(), healthy.getUserId());
    }

    // prepareGenerationContext는 PROCESSING을 누가 처리 중인줄 알고 건너뛴다.
    // 그대로 넘기면 아무 일도 일어나지 않으므로 먼저 상태를 풀어줘야 한다.
    @Test
    void 방치된_PROCESSING은_생성_대신_점유를_회수한다() {
        // given
        Feedback stale = feedback(FeedbackType.ENTRY_FEEDBACK);
        stale.updateStatus(FeedbackStatus.PROCESSING);
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of(stale));
        when(feedbackRepository.releaseStaleProcessing(any(), any(), any())).thenReturn(1);

        // when
        scheduler.retryFailedFeedbacks();

        // then
        verify(feedbackRepository).releaseStaleProcessing(any(), any(), any());
        verify(learningCommandService, never()).createEntryFeedback(any(), any());
    }

    // 측정이나 장애 대응 중에 끌 수 있어야 한다.
    @Test
    void 비활성화하면_조회조차_하지_않는다() {
        // given
        properties.setEnabled(false);

        // when
        scheduler.retryFailedFeedbacks();

        // then
        verifyNoInteractions(feedbackRepository, learningCommandService);
    }

    // 회수 기준이 AI 호출 시간보다 짧으면 정상 처리 중인 건을 뺏는다.
    @Test
    void 회수_기준_시각을_설정값으로_계산한다() {
        // given
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());
        OffsetDateTime before = OffsetDateTime.now().minus(properties.getStaleProcessingAfter());

        // when
        scheduler.retryFailedFeedbacks();

        // then
        ArgumentCaptor<OffsetDateTime> staleCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(feedbackRepository).findRetryTargets(
                any(), any(), anyInt(), staleCaptor.capture(), any());

        // 기준 시각이 "지금 - staleProcessingAfter" 근처여야 한다
        OffsetDateTime after = OffsetDateTime.now().minus(properties.getStaleProcessingAfter());
        org.junit.jupiter.api.Assertions.assertTrue(
                !staleCaptor.getValue().isBefore(before) && !staleCaptor.getValue().isAfter(after));
    }

    // 평소에는 재처리할 것이 없으므로 불필요한 호출이 없어야 한다.
    @Test
    void 대상이_없으면_생성_흐름을_호출하지_않는다() {
        // given
        when(feedbackRepository.findRetryTargets(any(), any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        // when
        scheduler.retryFailedFeedbacks();

        // then
        verify(learningCommandService, never()).createEntryFeedback(any(), any());
        verify(learningCommandService, never()).createPositionReviewFeedback(any(), any());
    }
}
