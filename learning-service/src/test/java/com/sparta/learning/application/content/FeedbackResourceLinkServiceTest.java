package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackResourceRepository;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackResourceLinkServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private LearningResourceRepository learningResourceRepository;

    @Mock
    private FeedbackResourceRepository feedbackResourceRepository;

    private FeedbackResourceLinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new FeedbackResourceLinkService(
                feedbackRepository,
                learningResourceRepository,
                feedbackResourceRepository
        );
    }

    @Test
    @DisplayName("선택된 자료를 전달된 순서대로 피드백에 연결한다")
    void linksSelectedResourcesInOrder() {
        Long feedbackId = 10L;
        Feedback feedback = org.mockito.Mockito.mock(Feedback.class);
        LearningResource first = resource(101L);
        LearningResource second = resource(102L);

        when(feedbackRepository.findByIdForUpdate(feedbackId)).thenReturn(Optional.of(feedback));
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedbackResourceRepository.findAllByFeedbackIdOrderByDisplayOrderAscIdAsc(feedbackId))
                .thenReturn(List.of());
        when(learningResourceRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(second, first));

        int createdCount = linkService.linkResources(feedbackId, List.of(
                selection(first, "첫 번째 추천 이유"),
                selection(second, "두 번째 추천 이유")
        ));

        assertThat(createdCount).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedbackResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(feedbackResourceRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        link -> link.getLearningResource().getId(),
                        FeedbackResource::getRuleCode,
                        FeedbackResource::getDisplayOrder,
                        FeedbackResource::getRecommendationReason
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, "HIGH_CHASING_BUY", 1, "첫 번째 추천 이유"),
                        org.assertj.core.groups.Tuple.tuple(102L, "HIGH_CHASING_BUY", 2, "두 번째 추천 이유")
                );
    }

    @Test
    @DisplayName("이미 연결된 자료와 요청 목록 내부의 중복 자료를 저장하지 않는다")
    void skipsExistingAndDuplicatedResources() {
        Long feedbackId = 10L;
        Feedback feedback = org.mockito.Mockito.mock(Feedback.class);
        LearningResource existingResource = resource(101L);
        LearningResource newResource = resource(102L);
        FeedbackResource existingLink = org.mockito.Mockito.mock(FeedbackResource.class);

        when(existingLink.getLearningResource()).thenReturn(existingResource);
        when(existingLink.getDisplayOrder()).thenReturn(3);
        when(feedbackRepository.findByIdForUpdate(feedbackId)).thenReturn(Optional.of(feedback));
        when(feedback.getStatus()).thenReturn(FeedbackStatus.COMPLETED);
        when(feedbackResourceRepository.findAllByFeedbackIdOrderByDisplayOrderAscIdAsc(feedbackId))
                .thenReturn(List.of(existingLink));
        when(learningResourceRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(newResource));

        int createdCount = linkService.linkResources(feedbackId, List.of(
                selection(existingResource, "기존 자료"),
                selection(newResource, "새 자료"),
                selection(newResource, "중복 자료")
        ));

        assertThat(createdCount).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedbackResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(feedbackResourceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(link -> {
            assertThat(link.getLearningResource()).isSameAs(newResource);
            assertThat(link.getRuleCode()).isEqualTo("HIGH_CHASING_BUY");
            assertThat(link.getDisplayOrder()).isEqualTo(4);
            assertThat(link.getRecommendationReason()).isEqualTo("새 자료");
        });
    }

    @Test
    @DisplayName("선택된 자료가 없으면 DB를 조회하지 않는다")
    void doesNothingWhenSelectionIsEmpty() {
        int createdCount = linkService.linkResources(10L, List.of());

        assertThat(createdCount).isZero();
        verify(feedbackRepository, never()).findByIdForUpdate(10L);
        verify(feedbackResourceRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("완료되지 않은 피드백에는 학습 자료를 연결하지 않는다")
    void rejectsIncompleteFeedback() {
        Long feedbackId = 10L;
        Feedback feedback = org.mockito.Mockito.mock(Feedback.class);
        LearningResource resource = org.mockito.Mockito.mock(LearningResource.class);

        when(feedbackRepository.findByIdForUpdate(feedbackId)).thenReturn(Optional.of(feedback));
        when(feedback.getStatus()).thenReturn(FeedbackStatus.PENDING);

        assertThatThrownBy(() -> linkService.linkResources(
                feedbackId,
                List.of(selection(resource, "추천 이유"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("완료되지 않은 피드백에는 학습 자료를 연결할 수 없습니다.");

        verify(feedbackResourceRepository, never())
                .findAllByFeedbackIdOrderByDisplayOrderAscIdAsc(feedbackId);
        verify(feedbackResourceRepository, never()).saveAll(anyList());
    }

    private SelectedLearningResource selection(LearningResource resource, String reason) {
        return new SelectedLearningResource(resource, "HIGH_CHASING_BUY", reason);
    }

    private LearningResource resource(Long id) {
        LearningResource resource = org.mockito.Mockito.mock(LearningResource.class);
        when(resource.getId()).thenReturn(id);
        return resource;
    }
}
