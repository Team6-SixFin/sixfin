package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.LearningResource;

/** 피드백에 연결하기 위해 선택한 학습 자료와 선택 근거 */
public record SelectedLearningResource(
        LearningResource resource,
        String ruleCode,
        String recommendationReason
) {
}
