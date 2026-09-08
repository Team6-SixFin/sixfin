package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackLearningResourceServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private LearningResourceSelectionService selectionService;

    @Mock
    private FeedbackResourceLinkService linkService;

    private FeedbackLearningResourceService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackLearningResourceService(
                feedbackRepository,
                selectionService,
                linkService
        );
    }

    @Test
    @DisplayName("완료된 피드백은 학습 자료를 선정하고 연결한다")
    void recommendsAndLinksResourcesForCompletedFeedback() {
        String feedbackKey = "ON_DEMAND_FEEDBACK:position:execution";
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        Feedback feedback = mock(Feedback.class);
        SelectedLearningResource selection = mock(SelectedLearningResource.class);

        when(feedback.getId()).thenReturn(10L);
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedbackRepository.findByFeedbackKey(feedbackKey)).thenReturn(Optional.of(feedback));
        when(selectionService.select(userId, positionId, FeedbackType.ON_DEMAND_FEEDBACK))
                .thenReturn(List.of(selection));
        when(linkService.linkResources(10L, List.of(selection))).thenReturn(1);

        int linkedCount = service.recommendAndLink(
                feedbackKey,
                userId,
                positionId,
                FeedbackType.ON_DEMAND_FEEDBACK
        );

        assertThat(linkedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("완료되지 않은 피드백은 외부 후보를 조회하지 않는다")
    void rejectsIncompleteFeedbackBeforeSelection() {
        String feedbackKey = "ON_DEMAND_FEEDBACK:position:execution";
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        Feedback feedback = mock(Feedback.class);

        when(feedback.getStatus()).thenReturn(FeedbackStatus.PENDING);
        when(feedbackRepository.findByFeedbackKey(feedbackKey)).thenReturn(Optional.of(feedback));

        assertThatThrownBy(() -> service.recommendAndLink(
                feedbackKey,
                userId,
                positionId,
                FeedbackType.ON_DEMAND_FEEDBACK
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("완료되지 않은 피드백에는 학습 자료를 추천할 수 없습니다.");

        verify(selectionService, never()).select(userId, positionId, FeedbackType.ON_DEMAND_FEEDBACK);
        verify(linkService, never()).linkResources(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }
}
