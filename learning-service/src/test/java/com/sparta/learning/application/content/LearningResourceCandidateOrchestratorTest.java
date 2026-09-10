package com.sparta.learning.application.content;

import com.sparta.learning.application.exception.LearningResourceSearchException;
import com.sparta.learning.application.port.LearningResourceSearchPort;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceCandidateOrchestratorTest {

    @Mock
    private LearningResourceCandidateService candidateService;

    @Mock
    private LearningResourceRecommendationPolicy recommendationPolicy;

    @Mock
    private LearningResourceCatalogService catalogService;

    @Mock
    private LearningResourceSearchPort searchPort;

    private LearningResourceCacheProperties cacheProperties;
    private LearningResourceCandidateOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        cacheProperties = new LearningResourceCacheProperties();
        orchestrator = new LearningResourceCandidateOrchestrator(
                candidateService,
                recommendationPolicy,
                catalogService,
                cacheProperties,
                List.of(searchPort)
        );
    }

    @Test
    @DisplayName("저장된 후보가 충분하면 외부 검색 없이 반환한다")
    void returnsStoredCandidatesWithoutExternalSearch() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        List<LearningResource> storedCandidates = List.of(resource(), resource());

        when(candidateService.findCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(storedCandidates);

        List<LearningResource> result = orchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        );

        assertThat(result).isSameAs(storedCandidates);
        verify(searchPort, never()).search("주식 고점 추격 매수 위험 예방", 20);
        verify(catalogService, never()).refresh(
                "HIGH_CHASING_BUY",
                "주식 고점 추격 매수 위험 예방",
                ResourceProvider.YOUTUBE,
                ResourceType.VIDEO,
                List.of()
        );
    }

    @Test
    @DisplayName("후보가 부족하면 외부 검색 결과를 저장하고 다시 조회한다")
    void refreshesCatalogAndFindsCandidatesAgain() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        List<LearningResource> initialCandidates = List.of();
        List<LearningResource> refreshedCandidates = List.of(resource(), resource());
        List<DiscoveredLearningResource> discoveries = List.of(discovery());

        when(candidateService.findCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(initialCandidates, refreshedCandidates);
        when(recommendationPolicy.findSearchTerms("HIGH_CHASING_BUY"))
                .thenReturn(Optional.of(new LearningResourceRecommendationPolicy.SearchTerms(
                        "고점 추격 매수 예방",
                        "주식 고점 추격 매수 위험 예방",
                        "고점 추격 매수 투자자 행동 편향 가이드"
                )));
        when(searchPort.resourceType()).thenReturn(ResourceType.VIDEO);
        when(searchPort.provider()).thenReturn(ResourceProvider.YOUTUBE);
        when(searchPort.search("주식 고점 추격 매수 위험 예방", 20)).thenReturn(discoveries);
        when(catalogService.refresh(
                "HIGH_CHASING_BUY",
                "주식 고점 추격 매수 위험 예방",
                ResourceProvider.YOUTUBE,
                ResourceType.VIDEO,
                discoveries
        )).thenReturn(new LearningResourceRefreshResult(1, 0));

        List<LearningResource> result = orchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        );

        assertThat(result).isSameAs(refreshedCandidates);
        verify(candidateService, org.mockito.Mockito.times(2)).findCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        );
    }

    @Test
    @DisplayName("외부 검색 실패 시 기존 후보만 반환한다")
    void fallsBackToStoredCandidatesWhenExternalSearchFails() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        List<LearningResource> initialCandidates = List.of(resource());

        when(candidateService.findCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        )).thenReturn(initialCandidates);
        when(recommendationPolicy.findSearchTerms("HIGH_CHASING_BUY"))
                .thenReturn(Optional.of(new LearningResourceRecommendationPolicy.SearchTerms(
                        "고점 추격 매수 예방",
                        "주식 고점 추격 매수 위험 예방",
                        "고점 추격 매수 투자자 행동 편향 가이드"
                )));
        when(searchPort.resourceType()).thenReturn(ResourceType.VIDEO);
        when(searchPort.provider()).thenReturn(ResourceProvider.YOUTUBE);
        when(searchPort.search("주식 고점 추격 매수 위험 예방", 20))
                .thenThrow(new LearningResourceSearchException("YouTube 호출 실패"));

        List<LearningResource> result = orchestrator.findOrRefreshCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        );

        assertThat(result).isSameAs(initialCandidates);
        verify(catalogService, never()).refresh(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyList()
        );
    }

    private LearningResource resource() {
        return org.mockito.Mockito.mock(LearningResource.class);
    }

    private DiscoveredLearningResource discovery() {
        return new DiscoveredLearningResource(
                "video-id",
                "영상 제목",
                "영상 설명",
                "channel-id",
                "채널명",
                "https://www.youtube.com/watch?v=video-id",
                null,
                null,
                300,
                1_000L
        );
    }
}
