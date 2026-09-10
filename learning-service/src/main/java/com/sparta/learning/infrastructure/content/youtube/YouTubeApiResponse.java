package com.sparta.learning.infrastructure.content.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

final class YouTubeApiResponse {

    private YouTubeApiResponse() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<SearchItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchItem(SearchId id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchId(String videoId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoResponse(List<VideoItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VideoItem(
            String id,
            Snippet snippet,
            ContentDetails contentDetails,
            Statistics statistics,
            Status status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Snippet(
            String title,
            String description,
            String channelId,
            String channelTitle,
            OffsetDateTime publishedAt,
            Map<String, Thumbnail> thumbnails
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentDetails(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Statistics(String viewCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Status(String privacyStatus, Boolean embeddable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Thumbnail(String url) {
    }
}
