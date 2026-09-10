package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackResourceRepository;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 선택된 학습 자료를 피드백과 연결하고 같은 연결의 중복 저장을 방지 */
@Service
@RequiredArgsConstructor
public class FeedbackResourceLinkService {

    private final FeedbackRepository feedbackRepository;
    private final LearningResourceRepository learningResourceRepository;
    private final FeedbackResourceRepository feedbackResourceRepository;

    @Transactional
    public int linkResources(Long feedbackId, List<SelectedLearningResource> selections) {
        if (feedbackId == null) {
            throw new IllegalArgumentException("피드백 ID는 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(selections, "선택된 학습 자료 목록은 null일 수 없습니다.");
        if (selections.isEmpty()) {
            return 0;
        }

        // 같은 피드백의 동시 연결을 직렬화해 UNIQUE 제약 충돌과 displayOrder 중복을 방지
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FEEDBACK_NOT_FOUND));
        if (feedback.getStatus() != FeedbackStatus.COMPLETED) {
            throw new IllegalStateException("완료되지 않은 피드백에는 학습 자료를 연결할 수 없습니다.");
        }

        List<FeedbackResource> existingLinks =
                feedbackResourceRepository.findAllByFeedbackIdOrderByDisplayOrderAscIdAsc(feedbackId);
        Set<Long> linkedResourceIds = new LinkedHashSet<>();
        int nextDisplayOrder = 1;

        for (FeedbackResource existingLink : existingLinks) {
            linkedResourceIds.add(existingLink.getLearningResource().getId());
            nextDisplayOrder = Math.max(nextDisplayOrder, existingLink.getDisplayOrder() + 1);
        }

        LinkedHashMap<Long, SelectedLearningResource> newSelectionByResourceId = new LinkedHashMap<>();
        for (SelectedLearningResource selection : selections) {
            validateSelection(selection);
            Long resourceId = selection.resource().getId();
            if (!linkedResourceIds.contains(resourceId)) {
                newSelectionByResourceId.putIfAbsent(resourceId, selection);
            }
        }

        if (newSelectionByResourceId.isEmpty()) {
            return 0;
        }

        Map<Long, LearningResource> managedResourceById = new LinkedHashMap<>();
        learningResourceRepository.findAllById(newSelectionByResourceId.keySet())
                .forEach(resource -> managedResourceById.put(resource.getId(), resource));

        if (managedResourceById.size() != newSelectionByResourceId.size()) {
            Set<Long> missingResourceIds = new LinkedHashSet<>(newSelectionByResourceId.keySet());
            missingResourceIds.removeAll(managedResourceById.keySet());
            throw new IllegalStateException("저장할 학습 자료를 찾을 수 없습니다. resourceIds=" + missingResourceIds);
        }

        List<FeedbackResource> newLinks = new ArrayList<>();
        for (Map.Entry<Long, SelectedLearningResource> entry : newSelectionByResourceId.entrySet()) {
            SelectedLearningResource selection = entry.getValue();
            newLinks.add(FeedbackResource.builder()
                    .feedback(feedback)
                    .learningResource(managedResourceById.get(entry.getKey()))
                    .ruleCode(selection.ruleCode())
                    .displayOrder(nextDisplayOrder++)
                    .recommendationReason(selection.recommendationReason())
                    .build());
        }

        feedbackResourceRepository.saveAll(newLinks);
        return newLinks.size();
    }

    private void validateSelection(SelectedLearningResource selection) {
        if (selection == null || selection.resource() == null || selection.resource().getId() == null) {
            throw new IllegalArgumentException("저장되지 않은 학습 자료는 피드백에 연결할 수 없습니다.");
        }
        if (selection.ruleCode() == null || selection.ruleCode().isBlank()) {
            throw new IllegalArgumentException("추천 원인이 된 진단 규칙 코드는 비어 있을 수 없습니다.");
        }
    }
}
