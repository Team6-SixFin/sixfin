package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackResourceResponseTest {

    @Test
    void returnsStoredResourceTypeInsteadOfHardCodedVideo() {
        LearningResource document = LearningResource.builder()
                .ruleCode("HIGH_CHASING_BUY")
                .searchQuery("고점 추격 매수 위험")
                .provider(ResourceProvider.OFFICIAL_SITE)
                .resourceType(ResourceType.DOCUMENT)
                .externalId("high-chasing-guide")
                .title("고점 추격 매수를 점검하는 방법")
                .url("https://example.com/high-chasing-guide")
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        Feedback feedback = Feedback.builder()
                .feedbackKey("feedback-key")
                .userId(UUID.randomUUID())
                .positionId(UUID.randomUUID())
                .feedbackType(FeedbackType.ON_DEMAND_FEEDBACK)
                .build();
        FeedbackResource feedbackResource = FeedbackResource.builder()
                .feedback(feedback)
                .learningResource(document)
                .ruleCode("HIGH_CHASING_BUY")
                .displayOrder(1)
                .recommendationReason("현재 진단과 관련된 공식 학습 자료입니다.")
                .build();

        FeedbackResourceResponse response = FeedbackResourceResponse.from(feedbackResource);

        assertThat(response.resourceType()).isEqualTo("DOCUMENT");
    }
}
