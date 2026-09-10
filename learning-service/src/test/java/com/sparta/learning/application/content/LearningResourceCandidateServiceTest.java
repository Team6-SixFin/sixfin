package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCacheProperties;
import com.sparta.learning.infrastructure.cache.LearningResourceCandidateCache;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceCandidateQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceCandidateServiceTest {

    @Mock
    private LearningResourceCandidateCache candidateCache;

    @Mock
    private LearningResourceCandidateQueryRepository candidateQueryRepository;

    private LearningResourceCandidateService candidateService;

    @BeforeEach
    void setUp() {
        LearningResourceCacheProperties cacheProperties = new LearningResourceCacheProperties();
        candidateService = new LearningResourceCandidateService(
                candidateCache,
                cacheProperties,
                candidateQueryRepository
        );
    }

    @Test
    void usesRedisCandidateOrderAndAppliesPersonalExclusionInDatabase() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        LearningResource first = candidate(10L);
        LearningResource second = candidate(20L);

        when(candidateCache.get("HIGH_CHASING_BUY", ResourceType.VIDEO))
                .thenReturn(Optional.of(List.of(20L, 10L)));
        when(candidateQueryRepository.findReusableCandidatesByIds(
                eq(List.of(20L, 10L)),
                eq(userId),
                eq(positionId),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(first, second));

        List<LearningResource> result = candidateService.findCandidates(
                userId,
                positionId,
                "HIGH_CHASING_BUY",
                ResourceType.VIDEO,
                2
        );

        assertThat(result).containsExactly(second, first);
        verify(candidateQueryRepository, never()).findPoolCandidates(any(), any(), any(), anyLong());
    }

    @Test
    void loadsDatabasePoolAndCachesIdsOnRedisMiss() {
        UUID userId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        LearningResource first = candidate(31L);
        LearningResource second = candidate(32L);

        when(candidateCache.get("STOP_LOSS_SET", ResourceType.DOCUMENT))
                .thenReturn(Optional.empty());
        when(candidateQueryRepository.findPoolCandidates(
                eq("STOP_LOSS_SET"),
                eq(ResourceType.DOCUMENT),
                any(OffsetDateTime.class),
                eq(20L)
        )).thenReturn(List.of(first, second));
        when(candidateQueryRepository.findReusableCandidatesByIds(
                eq(List.of(31L, 32L)),
                eq(userId),
                eq(positionId),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(first, second));

        List<LearningResource> result = candidateService.findCandidates(
                userId,
                positionId,
                "STOP_LOSS_SET",
                ResourceType.DOCUMENT,
                2
        );

        assertThat(result).containsExactly(first, second);
        verify(candidateCache).put("STOP_LOSS_SET", ResourceType.DOCUMENT, List.of(31L, 32L));
    }

    @Test
    void returnsEmptyWithoutQueryingWhenLimitIsNotPositive() {
        List<LearningResource> result = candidateService.findCandidates(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "STOP_LOSS_SET",
                ResourceType.VIDEO,
                0
        );

        assertThat(result).isEmpty();
        verify(candidateCache, never()).get(any(), any());
        verify(candidateQueryRepository, never()).findPoolCandidates(any(), any(), any(), anyLong());
    }

    private LearningResource candidate(long id) {
        LearningResource candidate = mock(LearningResource.class);
        when(candidate.getId()).thenReturn(id);
        return candidate;
    }
}
