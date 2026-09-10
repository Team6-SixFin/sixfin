package com.sparta.learning.application.dto.response;

import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.domain.entity.LearningResource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "피드백과 연결된 추천 학습 자료")
public record FeedbackResourceResponse(
        @Schema(description = "Learning 학습 자료 ID", example = "501")
        Long resourceId,

        @Schema(description = "학습 자료 유형", example = "VIDEO", allowableValues = {"VIDEO"})
        String resourceType,

        @Schema(description = "자료 제목", example = "손절 원칙을 지키는 방법")
        String title,

        @Schema(description = "학습 자료 URL", example = "https://www.youtube.com/watch?v=example")
        String url,

        @Schema(description = "영상 채널명", example = "SixFin Learning")
        String channelName,

        @Schema(description = "썸네일 이미지 URL", example = "https://img.youtube.com/vi/example/hqdefault.jpg")
        String thumbnailUrl,

        @Schema(description = "이 자료를 추천한 이유", example = "고점 추격 매수 경고가 반복되어 관련 자료를 추천했습니다.")
        String recommendationReason
) {
    public static FeedbackResourceResponse from(FeedbackResource feedbackResource) {
        LearningResource resource = feedbackResource.getLearningResource();

        return new FeedbackResourceResponse(
                resource.getId(),
                resource.getResourceType().name(),
                resource.getTitle(),
                resource.getUrl(),
                resource.getChannelName(),
                resource.getThumbnailUrl(),
                feedbackResource.getRecommendationReason()
        );
    }
}
