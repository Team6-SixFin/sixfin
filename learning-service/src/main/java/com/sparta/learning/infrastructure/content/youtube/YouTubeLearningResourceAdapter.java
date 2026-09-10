package com.sparta.learning.infrastructure.content.youtube;

import com.sparta.learning.application.content.DiscoveredLearningResource;
import com.sparta.learning.application.exception.LearningResourceSearchException;
import com.sparta.learning.application.port.LearningResourceSearchPort;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.config.YouTubeLearningResourceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** YouTube Data API로 교육 영상을 검색하고 공통 검색 결과로 변환합니다. */
@Component
public class YouTubeLearningResourceAdapter implements LearningResourceSearchPort {

    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final int YOUTUBE_MAX_RESULTS = 50;
    private static final List<String> THUMBNAIL_PRIORITY =
            List.of("maxres", "standard", "high", "medium", "default");

    private final RestClient restClient;
    private final YouTubeLearningResourceProperties properties;

    public YouTubeLearningResourceAdapter(
            RestClient.Builder restClientBuilder,
            YouTubeLearningResourceProperties properties
    ) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(properties.getBaseUrl())
                .build();
        this.properties = properties;
    }

    @Override
    public ResourceProvider provider() {
        return ResourceProvider.YOUTUBE;
    }

    @Override
    public ResourceType resourceType() {
        return ResourceType.VIDEO;
    }

    @Override
    public List<DiscoveredLearningResource> search(String query, int limit) {
        if (!properties.isEnabled() || limit < 1) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("YouTube 검색어는 비어 있을 수 없습니다.");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LearningResourceSearchException("YouTube API 키가 설정되지 않았습니다.");
        }

        int requestSize = Math.min(
                Math.min(limit, properties.getMaxResults()),
                YOUTUBE_MAX_RESULTS
        );

        try {
            List<String> videoIds = searchVideoIds(query, requestSize);
            if (videoIds.isEmpty()) {
                return List.of();
            }
            return findVideoDetails(videoIds);
        } catch (RestClientException exception) {
            throw new LearningResourceSearchException("YouTube 교육 영상 검색에 실패했습니다.", exception);
        }
    }

    private List<String> searchVideoIds(String query, int requestSize) {
        YouTubeApiResponse.SearchResponse response = restClient.get()
                .uri(uriBuilder -> searchUri(uriBuilder, query, requestSize))
                .header(API_KEY_HEADER, properties.getApiKey())
                .retrieve()
                .body(YouTubeApiResponse.SearchResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        return response.items().stream()
                .filter(Objects::nonNull)
                .map(YouTubeApiResponse.SearchItem::id)
                .filter(Objects::nonNull)
                .map(YouTubeApiResponse.SearchId::videoId)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private java.net.URI searchUri(UriBuilder uriBuilder, String query, int requestSize) {
        UriBuilder builder = uriBuilder
                .path("/search")
                .queryParam("part", "snippet")
                .queryParam("q", query)
                .queryParam("type", "video")
                .queryParam("order", "relevance")
                .queryParam("videoEmbeddable", "true")
                .queryParam("safeSearch", "moderate")
                .queryParam("maxResults", requestSize);

        if (hasText(properties.getRelevanceLanguage())) {
            builder.queryParam("relevanceLanguage", properties.getRelevanceLanguage());
        }
        if (hasText(properties.getRegionCode())) {
            builder.queryParam("regionCode", properties.getRegionCode());
        }
        return builder.build();
    }

    private List<DiscoveredLearningResource> findVideoDetails(List<String> videoIds) {
        YouTubeApiResponse.VideoResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/videos")
                        .queryParam("part", "snippet,contentDetails,statistics,status")
                        .queryParam("id", String.join(",", videoIds))
                        .build())
                .header(API_KEY_HEADER, properties.getApiKey())
                .retrieve()
                .body(YouTubeApiResponse.VideoResponse.class);

        if (response == null || response.items() == null) {
            return List.of();
        }

        Map<String, YouTubeApiResponse.VideoItem> detailsById = response.items().stream()
                .filter(this::isUsable)
                .collect(Collectors.toMap(
                        YouTubeApiResponse.VideoItem::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        return videoIds.stream()
                .map(detailsById::get)
                .filter(Objects::nonNull)
                .map(this::toDiscoveredResource)
                .toList();
    }

    private boolean isUsable(YouTubeApiResponse.VideoItem video) {
        return video != null
                && hasText(video.id())
                && video.snippet() != null
                && hasText(video.snippet().title())
                && video.status() != null
                && "public".equalsIgnoreCase(video.status().privacyStatus())
                && Boolean.TRUE.equals(video.status().embeddable());
    }

    private DiscoveredLearningResource toDiscoveredResource(YouTubeApiResponse.VideoItem video) {
        YouTubeApiResponse.Snippet snippet = video.snippet();
        return new DiscoveredLearningResource(
                video.id(),
                snippet.title(),
                snippet.description(),
                snippet.channelId(),
                snippet.channelTitle(),
                "https://www.youtube.com/watch?v=" + video.id(),
                bestThumbnailUrl(snippet.thumbnails()),
                snippet.publishedAt(),
                durationSeconds(video.contentDetails()),
                viewCount(video.statistics())
        );
    }

    private String bestThumbnailUrl(Map<String, YouTubeApiResponse.Thumbnail> thumbnails) {
        if (thumbnails == null || thumbnails.isEmpty()) {
            return null;
        }
        return THUMBNAIL_PRIORITY.stream()
                .map(thumbnails::get)
                .filter(Objects::nonNull)
                .map(YouTubeApiResponse.Thumbnail::url)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private Integer durationSeconds(YouTubeApiResponse.ContentDetails contentDetails) {
        if (contentDetails == null || !hasText(contentDetails.duration())) {
            return null;
        }
        try {
            return Math.toIntExact(Duration.parse(contentDetails.duration()).toSeconds());
        } catch (ArithmeticException | java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private Long viewCount(YouTubeApiResponse.Statistics statistics) {
        if (statistics == null || !hasText(statistics.viewCount())) {
            return null;
        }
        try {
            return Long.parseLong(statistics.viewCount());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
