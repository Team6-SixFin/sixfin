package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceStatus;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCandidateCache;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceCatalogServiceTest {

    @Mock
    private LearningResourceRepository learningResourceRepository;

    @Mock
    private LearningResourceFreshnessPolicy freshnessPolicy;

    @Mock
    private LearningResourceCandidateCache candidateCache;

    @Test
    void createsNewResourcesRefreshesExistingResourcesAndEvictsCache() {
        LearningResourceCatalogService service = service();
        LearningResource existing = existingResource(ResourceType.VIDEO);
        existing.markUnavailable(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7);
        AtomicReference<List<LearningResource>> savedResources = new AtomicReference<>();

        when(learningResourceRepository.findAllByRuleCodeAndProviderAndExternalIdIn(
                eq("HIGH_CHASING_BUY"),
                eq(ResourceProvider.YOUTUBE),
                anyCollection()
        )).thenReturn(List.of(existing));
        when(freshnessPolicy.expiresAt(eq(ResourceType.VIDEO), any()))
                .thenReturn(expiresAt);
        when(learningResourceRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<LearningResource> resources = invocation.getArgument(0);
            List<LearningResource> copied = new ArrayList<>();
            resources.forEach(copied::add);
            savedResources.set(copied);
            return copied;
        });

        LearningResourceRefreshResult result = service.refresh(
                "HIGH_CHASING_BUY",
                "고점 추격 매수 예방",
                ResourceProvider.YOUTUBE,
                ResourceType.VIDEO,
                List.of(
                        discovery("video-1", "갱신된 제목"),
                        discovery("video-2", "새 영상"),
                        discovery("video-2", "중복 검색 결과")
                )
        );

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(savedResources.get()).hasSize(2);
        assertThat(existing.getTitle()).isEqualTo("갱신된 제목");
        assertThat(existing.getStatus()).isEqualTo(ResourceStatus.ACTIVE);
        assertThat(existing.getExpiresAt()).isEqualTo(expiresAt);

        LearningResource created = savedResources.get().stream()
                .filter(resource -> resource.getExternalId().equals("video-2"))
                .findFirst()
                .orElseThrow();
        assertThat(created.getTitle()).isEqualTo("새 영상");
        assertThat(created.getSearchedAt()).isNotNull();
        assertThat(created.getLastVerifiedAt()).isEqualTo(created.getSearchedAt());
        assertThat(created.getExpiresAt()).isEqualTo(expiresAt);

        var ordered = inOrder(learningResourceRepository, candidateCache);
        ordered.verify(learningResourceRepository).saveAll(any());
        ordered.verify(candidateCache).evict("HIGH_CHASING_BUY", ResourceType.VIDEO);
    }

    @Test
    void emptySearchResultDoesNotChangeDatabaseOrCache() {
        LearningResourceRefreshResult result = service().refresh(
                "STOP_LOSS_SET",
                "손절 계획",
                ResourceProvider.OFFICIAL_SITE,
                ResourceType.DOCUMENT,
                List.of()
        );

        assertThat(result).isEqualTo(LearningResourceRefreshResult.empty());
        verifyNoInteractions(learningResourceRepository, freshnessPolicy, candidateCache);
    }

    @Test
    void rejectsChangingTypeOfSameExternalResource() {
        LearningResource existing = existingResource(ResourceType.VIDEO);
        when(learningResourceRepository.findAllByRuleCodeAndProviderAndExternalIdIn(
                anyString(),
                eq(ResourceProvider.YOUTUBE),
                anyCollection()
        )).thenReturn(List.of(existing));
        when(freshnessPolicy.expiresAt(eq(ResourceType.DOCUMENT), any()))
                .thenReturn(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));

        assertThatThrownBy(() -> service().refresh(
                "HIGH_CHASING_BUY",
                "고점 추격 문서",
                ResourceProvider.YOUTUBE,
                ResourceType.DOCUMENT,
                List.of(discovery("video-1", "잘못된 유형"))
        )).isInstanceOf(IllegalStateException.class);

        verify(learningResourceRepository, never()).saveAll(any());
        verify(candidateCache, never()).evict(anyString(), any());
    }

    private LearningResourceCatalogService service() {
        return new LearningResourceCatalogService(
                learningResourceRepository,
                freshnessPolicy,
                candidateCache
        );
    }

    private LearningResource existingResource(ResourceType resourceType) {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
        return LearningResource.builder()
                .ruleCode("HIGH_CHASING_BUY")
                .searchQuery("이전 검색어")
                .provider(ResourceProvider.YOUTUBE)
                .resourceType(resourceType)
                .externalId("video-1")
                .title("이전 제목")
                .url("https://youtube.com/watch?v=video-1")
                .searchedAt(createdAt)
                .lastVerifiedAt(createdAt)
                .expiresAt(createdAt.plusDays(7))
                .build();
    }

    private DiscoveredLearningResource discovery(String externalId, String title) {
        return new DiscoveredLearningResource(
                externalId,
                title,
                "설명",
                "channel-id",
                "채널",
                "https://youtube.com/watch?v=" + externalId,
                "https://img.youtube.com/" + externalId,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                600,
                10_000L
        );
    }
}
