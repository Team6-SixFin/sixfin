package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceProvider;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCandidateCache;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 외부에서 발견한 학습 자료를 PostgreSQL의 후보 카탈로그에 반영합니다. */
@Service
@RequiredArgsConstructor
public class LearningResourceCatalogService {

    private final LearningResourceRepository learningResourceRepository;
    private final LearningResourceFreshnessPolicy freshnessPolicy;
    private final LearningResourceCandidateCache candidateCache;

    @Transactional
    public LearningResourceRefreshResult refresh(
            String ruleCode,
            String searchQuery,
            ResourceProvider provider,
            ResourceType resourceType,
            List<DiscoveredLearningResource> discoveries
    ) {
        validate(ruleCode, searchQuery, provider, resourceType, discoveries);

        Map<String, DiscoveredLearningResource> discoveryByExternalId = discoveries.stream()
                .collect(Collectors.toMap(
                        DiscoveredLearningResource::externalId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        if (discoveryByExternalId.isEmpty()) {
            return LearningResourceRefreshResult.empty();
        }

        Map<String, LearningResource> existingByExternalId = learningResourceRepository
                .findAllByRuleCodeAndProviderAndExternalIdIn(
                        ruleCode,
                        provider,
                        discoveryByExternalId.keySet()
                ).stream()
                .collect(Collectors.toMap(LearningResource::getExternalId, Function.identity()));

        OffsetDateTime refreshedAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = freshnessPolicy.expiresAt(resourceType, refreshedAt);
        int createdCount = 0;
        int updatedCount = 0;

        for (DiscoveredLearningResource discovery : discoveryByExternalId.values()) {
            LearningResource existing = existingByExternalId.get(discovery.externalId());
            if (existing == null) {
                existingByExternalId.put(
                        discovery.externalId(),
                        newResource(ruleCode, searchQuery, provider, resourceType, discovery, refreshedAt, expiresAt)
                );
                createdCount++;
            } else {
                if (existing.getResourceType() != resourceType) {
                    throw new IllegalStateException("동일한 외부 자료의 자료 유형을 변경할 수 없습니다. externalId=" + discovery.externalId());
                }
                refresh(existing, searchQuery, discovery, refreshedAt, expiresAt);
                updatedCount++;
            }
        }

        learningResourceRepository.saveAll(existingByExternalId.values());
        candidateCache.evict(ruleCode, resourceType);
        return new LearningResourceRefreshResult(createdCount, updatedCount);
    }

    private LearningResource newResource(
            String ruleCode,
            String searchQuery,
            ResourceProvider provider,
            ResourceType resourceType,
            DiscoveredLearningResource discovery,
            OffsetDateTime refreshedAt,
            OffsetDateTime expiresAt
    ) {
        return LearningResource.builder()
                .ruleCode(ruleCode)
                .searchQuery(searchQuery)
                .provider(provider)
                .resourceType(resourceType)
                .externalId(discovery.externalId())
                .title(discovery.title())
                .description(discovery.description())
                .channelId(discovery.channelId())
                .channelName(discovery.channelName())
                .url(discovery.url())
                .thumbnailUrl(discovery.thumbnailUrl())
                .publishedAt(discovery.publishedAt())
                .durationSeconds(discovery.durationSeconds())
                .viewCount(discovery.viewCount())
                .searchedAt(refreshedAt)
                .lastVerifiedAt(refreshedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private void refresh(
            LearningResource resource,
            String searchQuery,
            DiscoveredLearningResource discovery,
            OffsetDateTime refreshedAt,
            OffsetDateTime expiresAt
    ) {
        resource.refresh(
                searchQuery,
                discovery.title(),
                discovery.description(),
                discovery.channelId(),
                discovery.channelName(),
                discovery.url(),
                discovery.thumbnailUrl(),
                discovery.publishedAt(),
                discovery.durationSeconds(),
                discovery.viewCount(),
                refreshedAt,
                expiresAt
        );
    }

    private void validate(
            String ruleCode,
            String searchQuery,
            ResourceProvider provider,
            ResourceType resourceType,
            List<DiscoveredLearningResource> discoveries
    ) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("진단 규칙 코드는 비어 있을 수 없습니다.");
        }
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(provider, "외부 자료 제공자는 비어 있을 수 없습니다.");
        Objects.requireNonNull(resourceType, "학습 자료 유형은 비어 있을 수 없습니다.");
        Objects.requireNonNull(discoveries, "검색 결과는 null일 수 없습니다.");
    }
}
