package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;

public record FeedbackResourceResponse(
        Long resourceId,
        String resourceType,
        String title,
        String url,
        String channelName,
        String thumbnailUrl,
        String recommendationReason
) {

    private static final String VIDEO = "VIDEO";

    public static FeedbackResourceResponse from(FeedbackResource feedbackResource) {
        LearningResource resource = feedbackResource.getLearningResource();

        return new FeedbackResourceResponse(
                resource.getId(),
                VIDEO,
                resource.getTitle(),
                resource.getUrl(),
                resource.getChannelName(),
                resource.getThumbnailUrl(),
                feedbackResource.getRecommendationReason()
        );
    }
}
