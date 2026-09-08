package com.sparta.learning.infrastructure.content.youtube;

import com.sparta.learning.application.exception.LearningResourceSearchException;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.config.YouTubeLearningResourceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YouTubeLearningResourceAdapterTest {

    private YouTubeLearningResourceProperties properties;
    private MockRestServiceServer server;
    private YouTubeLearningResourceAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new YouTubeLearningResourceProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");
        properties.setBaseUrl("https://www.googleapis.com/youtube/v3");
        properties.setMaxResults(20);
        properties.setRelevanceLanguage("ko");
        properties.setRegionCode("KR");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new YouTubeLearningResourceAdapter(builder, properties);
    }

    @Test
    void searchesVideoDetailsFiltersUnavailableItemsAndPreservesSearchOrder() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/search")))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(queryParam("part", "snippet"))
                .andExpect(request -> assertThat(URLDecoder.decode(
                        request.getURI().getRawQuery(),
                        StandardCharsets.UTF_8
                )).contains("q=고점 추격 매수 예방"))
                .andExpect(queryParam("type", "video"))
                .andExpect(queryParam("videoEmbeddable", "true"))
                .andExpect(queryParam("maxResults", "3"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {"id": {"videoId": "video-2"}},
                            {"id": {"videoId": "video-1"}},
                            {"id": {"videoId": "video-3"}},
                            {"id": {"videoId": "video-2"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/videos")))
                .andExpect(header("X-Goog-Api-Key", "test-api-key"))
                .andExpect(queryParam("part", "snippet,contentDetails,statistics,status"))
                .andExpect(queryParam("id", "video-2,video-1,video-3"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "video-1",
                              "snippet": {
                                "title": "첫 번째 영상",
                                "description": "첫 번째 설명",
                                "channelId": "channel-1",
                                "channelTitle": "첫 번째 채널",
                                "publishedAt": "2026-08-01T01:00:00Z",
                                "thumbnails": {
                                  "medium": {"url": "https://img.example/video-1-medium.jpg"},
                                  "high": {"url": "https://img.example/video-1-high.jpg"}
                                }
                              },
                              "contentDetails": {"duration": "PT10M5S"},
                              "statistics": {"viewCount": "10000"},
                              "status": {"privacyStatus": "public", "embeddable": true}
                            },
                            {
                              "id": "video-2",
                              "snippet": {
                                "title": "두 번째 영상",
                                "description": "두 번째 설명",
                                "channelId": "channel-2",
                                "channelTitle": "두 번째 채널",
                                "publishedAt": "2026-08-02T01:00:00Z",
                                "thumbnails": {
                                  "medium": {"url": "https://img.example/video-2-medium.jpg"}
                                }
                              },
                              "contentDetails": {"duration": "PT1M5S"},
                              "statistics": {"viewCount": "20000"},
                              "status": {"privacyStatus": "public", "embeddable": true}
                            },
                            {
                              "id": "video-3",
                              "snippet": {
                                "title": "비공개 영상",
                                "publishedAt": "2026-08-03T01:00:00Z"
                              },
                              "contentDetails": {"duration": "PT3M"},
                              "statistics": {"viewCount": "30000"},
                              "status": {"privacyStatus": "private", "embeddable": false}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var resources = adapter.search("고점 추격 매수 예방", 3);

        assertThat(adapter.provider()).isEqualTo(ResourceProvider.YOUTUBE);
        assertThat(adapter.resourceType()).isEqualTo(ResourceType.VIDEO);
        assertThat(resources).extracting(resource -> resource.externalId())
                .containsExactly("video-2", "video-1");
        assertThat(resources.getFirst().durationSeconds()).isEqualTo(65);
        assertThat(resources.getFirst().viewCount()).isEqualTo(20_000L);
        assertThat(resources.getFirst().url())
                .isEqualTo("https://www.youtube.com/watch?v=video-2");
        assertThat(resources.get(1).thumbnailUrl())
                .isEqualTo("https://img.example/video-1-high.jpg");
        server.verify();
    }

    @Test
    void returnsEmptyWithoutCallingYouTubeWhenFeatureIsDisabled() {
        properties.setEnabled(false);

        assertThat(adapter.search("손절 계획", 10)).isEmpty();
        server.verify();
    }

    @Test
    void rejectsSearchWhenEnabledButApiKeyIsMissing() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> adapter.search("손절 계획", 10))
                .isInstanceOf(LearningResourceSearchException.class)
                .hasMessageContaining("API 키");
        server.verify();
    }

    @Test
    void capsSearchSizeAtConfiguredMaximum() {
        properties.setMaxResults(2);

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/youtube/v3/search")))
                .andExpect(queryParam("maxResults", "2"))
                .andRespond(withSuccess("{\"items\": []}", MediaType.APPLICATION_JSON));

        assertThat(adapter.search("손절 계획", 10)).isEmpty();
        server.verify();
    }
}
