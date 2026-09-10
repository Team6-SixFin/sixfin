package com.sparta.learning.infrastructure.cache;

import com.sparta.learning.domain.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceCandidateCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LearningResourceCandidateCache candidateCache;

    @BeforeEach
    void setUp() {
        LearningResourceCacheProperties properties = new LearningResourceCacheProperties();
        properties.setTtl(Duration.ofHours(24));
        candidateCache = new LearningResourceCandidateCache(redisTemplate, properties);
    }

    @Test
    void readsCandidateIdsFromRedis() {
        String key = "learning:resource:candidates:v1:HIGH_CHASING_BUY:VIDEO";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("31,42,31");

        assertThat(candidateCache.get("HIGH_CHASING_BUY", ResourceType.VIDEO))
                .contains(List.of(31L, 42L));
    }

    @Test
    void storesCandidateIdsWithConfiguredTtl() {
        String key = "learning:resource:candidates:v1:STOP_LOSS_SET:DOCUMENT";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        candidateCache.put("STOP_LOSS_SET", ResourceType.DOCUMENT, List.of(11L, 12L, 11L));

        verify(valueOperations).set(key, "11,12", Duration.ofHours(24));
    }

    @Test
    void returnsCacheMissWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));

        assertThat(candidateCache.get("STOP_LOSS_SET", ResourceType.VIDEO)).isEmpty();
    }

    @Test
    void evictsCandidatePoolWhenCatalogChanges() {
        candidateCache.evict("HIGH_CHASING_BUY", ResourceType.VIDEO);

        verify(redisTemplate).delete("learning:resource:candidates:v1:HIGH_CHASING_BUY:VIDEO");
    }

    @Test
    void redisFailureWhileEvictingDoesNotFailCatalogRefresh() {
        when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));

        assertThatCode(() -> candidateCache.evict("STOP_LOSS_SET", ResourceType.DOCUMENT))
                .doesNotThrowAnyException();
    }
}
