package com.sparta.learning.application.content;

import com.sparta.learning.application.exception.LearningResourceSearchException;
import com.sparta.learning.application.port.LearningResourceSearchPort;
import com.sparta.learning.domain.entity.LearningResource;
import com.sparta.learning.domain.model.ResourceType;
import com.sparta.learning.infrastructure.cache.LearningResourceCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 저장된 후보를 먼저 조회하고, 부족할 때만 외부 제공자를 검색해 후보 카탈로그를 보충
 * 외부 호출을 DB 트랜잭션 밖에서 실행하기 위해 이 클래스에는 @Transactional을 선언하지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningResourceCandidateOrchestrator {

    private final LearningResourceCandidateService candidateService;
    private final LearningResourceRecommendationPolicy recommendationPolicy;
    private final LearningResourceCatalogService catalogService;
    private final LearningResourceCacheProperties cacheProperties;
    private final List<LearningResourceSearchPort> searchPorts;

    public List<LearningResource> findOrRefreshCandidates(
            UUID userId,
            UUID positionId,
            String ruleCode,
            ResourceType resourceType,
            int limit
    ) {
        // 기존 후보 먼저 조회
        List<LearningResource> candidates = candidateService.findCandidates(
                userId,
                positionId,
                ruleCode,
                resourceType,
                limit
        );
        // 후보가 충분히 크면 호출 x -> 시간,비용 절약
        if (limit < 1 || candidates.size() >= limit) {
            return candidates;
        }

        // 진단 규칙으로 검색어 조회
        LearningResourceRecommendationPolicy.SearchTerms searchTerms = recommendationPolicy
                .findSearchTerms(ruleCode)
                .orElse(null);
        if (searchTerms == null) {
            return candidates;
        }

        // 검색어와 검색 개수 결정
        String searchQuery = searchQuery(searchTerms, resourceType);
        int searchLimit = Math.max(limit, cacheProperties.getPoolSize());
        boolean catalogChanged = refreshCatalog(ruleCode, resourceType, searchQuery, searchLimit);

        if (!catalogChanged) {
            return candidates;
        }

        return candidateService.findCandidates(
                userId,
                positionId,
                ruleCode,
                resourceType,
                limit
        );
    }

    private boolean refreshCatalog(
            String ruleCode,
            ResourceType resourceType,
            String searchQuery,
            int searchLimit
    ) {
        boolean catalogChanged = false;

        for (LearningResourceSearchPort searchPort : searchPorts) {
            // 자료 유형이 다른 어댑터 제외
            if (searchPort.resourceType() != resourceType) {
                continue;
            }

            try {
                // 외부 자료 검색
                List<DiscoveredLearningResource> discoveries = searchPort.search(searchQuery, searchLimit);
                if (discoveries.isEmpty()) {
                    continue;
                }

                /**
                *   외부 ID 중복 제거
                *   → 기존 자료 일괄 조회
                *   → 신규 자료 INSERT
                *   → 기존 자료 UPDATE
                *   → expiresAt 설정
                *   → Redis 후보 캐시 삭제
                 */
                LearningResourceRefreshResult result = catalogService.refresh(
                        ruleCode,
                        searchQuery,
                        searchPort.provider(),
                        searchPort.resourceType(),
                        discoveries
                );
                catalogChanged = catalogChanged || result.totalCount() > 0;
            } catch (LearningResourceSearchException exception) {
                // 학습 자료 검색 실패가 피드백 생성 전체 실패로 전파되지 않게 기존 후보만 사용
                log.warn(
                        "외부 학습 자료 검색 실패, 기존 후보만 사용합니다. ruleCode={}, provider={}, resourceType={}",
                        ruleCode,
                        searchPort.provider(),
                        resourceType,
                        exception
                );
            }
        }

        return catalogChanged;
    }

    private String searchQuery(
            LearningResourceRecommendationPolicy.SearchTerms searchTerms,
            ResourceType resourceType
    ) {
        return switch (resourceType) {
            case VIDEO -> searchTerms.youtubeQuery();
            case DOCUMENT -> searchTerms.documentQuery();
        };
    }
}
