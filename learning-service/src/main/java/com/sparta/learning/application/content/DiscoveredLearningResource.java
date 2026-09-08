package com.sparta.learning.application.content;

import java.time.OffsetDateTime;

/** 외부 검색 제공자가 반환한 자료를 저장 계층과 분리해 전달하는 공통 형식입니다. */
public record DiscoveredLearningResource(
        String externalId,
        String title,
        String description,
        String channelId,
        String channelName,
        String url,
        String thumbnailUrl,
        OffsetDateTime publishedAt,
        Integer durationSeconds,
        Long viewCount
) {

    public DiscoveredLearningResource {
        requireText(externalId, "외부 자료 ID");
        requireText(title, "자료 제목");
        requireText(url, "자료 URL");

        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("영상 길이는 음수일 수 없습니다.");
        }
        if (viewCount != null && viewCount < 0) {
            throw new IllegalArgumentException("조회 수는 음수일 수 없습니다.");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
    }
}
