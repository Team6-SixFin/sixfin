package com.sparta.learning.infrastructure.cache;

import com.sparta.learning.domain.model.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 진단 규칙과 자료 유형별 공통 후보 ID를 Redis에 저장
 * Redis 장애는 추천 전체 장애로 전파하지 않고 DB 조회로 fallback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningResourceCandidateCache {

    private static final String KEY_PREFIX = "learning:resource:candidates:v1:";

    private final StringRedisTemplate redisTemplate;
    private final LearningResourceCacheProperties cacheProperties;

    public Optional<List<Long>> get(String ruleCode, ResourceType resourceType) {
        String key = key(ruleCode, resourceType);

        try {
            String cachedValue = redisTemplate.opsForValue().get(key);
            if (cachedValue == null || cachedValue.isBlank()) {
                return Optional.empty();
            }

            List<Long> candidateIds = Arrays.stream(cachedValue.split(","))
                    .map(Long::valueOf)
                    .distinct()
                    .toList();
            return candidateIds.isEmpty() ? Optional.empty() : Optional.of(candidateIds);
        } catch (RuntimeException exception) {
            log.warn("학습 자료 후보 Redis 조회 실패, DB 조회로 전환합니다. key={}", key, exception);
            return Optional.empty();
        }
    }

    public void put(String ruleCode, ResourceType resourceType, List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return;
        }

        String key = key(ruleCode, resourceType);
        String cachedValue = candidateIds.stream()
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try {
            redisTemplate.opsForValue().set(key, cachedValue, cacheProperties.getTtl());
        } catch (RuntimeException exception) {
            log.warn("학습 자료 후보 Redis 저장 실패, DB 조회 결과만 사용합니다. key={}", key, exception);
        }
    }

    /** DB 후보군이 갱신되면 기존 ID 목록을 제거해 다음 조회에서 다시 구성 */
    public void evict(String ruleCode, ResourceType resourceType) {
        String key = key(ruleCode, resourceType);

        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("학습 자료 후보 Redis 삭제 실패, 기존 캐시는 TTL까지 유지됩니다. key={}", key, exception);
        }
    }

    static String key(String ruleCode, ResourceType resourceType) {
        return KEY_PREFIX + ruleCode + ":" + resourceType.name();
    }
}
