package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCacheProperties;
import com.sparta.learning.infrastructure.cache.LearningResourceCandidateCache;
import com.sparta.learning.infrastructure.persistence.repository.LearningResourceCandidateQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Redis 후보를 우선 사용하고, 캐시 미스 시 PostgreSQL에서 후보군을 복구 */
@Service
@RequiredArgsConstructor
public class LearningResourceCandidateService {

    private final LearningResourceCandidateCache candidateCache;
    private final LearningResourceCacheProperties cacheProperties;
    private final LearningResourceCandidateQueryRepository candidateQueryRepository;

    @Transactional(readOnly = true)
    public List<LearningResource> findCandidates(
            UUID userId,
            UUID positionId,
            String ruleCode,
            ResourceType resourceType,
            int limit
    ) {
        validate(userId, positionId, ruleCode, resourceType);
        if (limit < 1) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Long> candidateIds = candidateCache.get(ruleCode, resourceType)
                .orElseGet(() -> loadAndCachePool(ruleCode, resourceType, now));

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<LearningResource> reusableCandidates = candidateQueryRepository.findReusableCandidatesByIds(
                candidateIds,
                userId,
                positionId,
                now,
                now.minusDays(cacheProperties.getRecentRecommendationDays())
        );

        // DB의 IN 조회는 순서를 보장하지 않으므로 Redis 후보 순서로 복구
        Map<Long, LearningResource> candidateById = new LinkedHashMap<>();
        reusableCandidates.forEach(candidate -> candidateById.put(candidate.getId(), candidate));

        int boundedLimit = Math.min(limit, cacheProperties.getPoolSize());
        return candidateIds.stream()
                .map(candidateById::get)
                .filter(candidate -> candidate != null)
                .limit(boundedLimit)
                .toList();
    }

    private List<Long> loadAndCachePool(
            String ruleCode,
            ResourceType resourceType,
            OffsetDateTime now
    ) {
        List<Long> candidateIds = candidateQueryRepository.findPoolCandidates(
                        ruleCode,
                        resourceType,
                        now,
                        cacheProperties.getPoolSize()
                ).stream()
                .map(LearningResource::getId)
                .toList();

        candidateCache.put(ruleCode, resourceType, candidateIds);
        return candidateIds;
    }

    private void validate(
            UUID userId,
            UUID positionId,
            String ruleCode,
            ResourceType resourceType
    ) {
        if (userId == null || positionId == null || resourceType == null) {
            throw new IllegalArgumentException("후보 조회 식별값은 비어 있을 수 없습니다.");
        }
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("진단 규칙 코드는 비어 있을 수 없습니다.");
        }
    }
}
