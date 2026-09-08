package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 완료된 피드백에 진단 기반 학습 자료를 선정하고 연결하는 애플리케이션 서비스입니다. */
@Service
@RequiredArgsConstructor
public class FeedbackLearningResourceService {

    private final FeedbackRepository feedbackRepository;
    private final LearningResourceSelectionService selectionService;
    private final FeedbackResourceLinkService linkService;

    public int recommendAndLink(
            String feedbackKey,
            UUID userId,
            UUID positionId,
            FeedbackType feedbackType
    ) {
        Feedback feedback = feedbackRepository.findByFeedbackKey(feedbackKey)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FEEDBACK_NOT_FOUND));
        if (feedback.getStatus() != FeedbackStatus.COMPLETED) {
            throw new IllegalStateException("완료되지 않은 피드백에는 학습 자료를 추천할 수 없습니다.");
        }

        List<SelectedLearningResource> selections = selectionService.select(
                userId,
                positionId,
                feedbackType
        );
        return linkService.linkResources(feedback.getId(), selections);
    }
}
