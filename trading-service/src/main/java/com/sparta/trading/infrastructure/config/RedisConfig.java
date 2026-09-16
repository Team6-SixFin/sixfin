package com.sparta.trading.infrastructure.config;

import com.sparta.trading.application.port.Quote;
import com.sparta.trading.domain.entity.MarketClockSnapshot;
import com.sparta.trading.infrastructure.quote.QuoteWindowSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair.fromSerializer;

@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig implements CachingConfigurer {

    private final ObjectMapper objectMapper;

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory
    ){
        JacksonJsonRedisSerializer<MarketClockSnapshot> clockSerializer =
                new JacksonJsonRedisSerializer<>(objectMapper, MarketClockSnapshot.class);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(Duration.ofHours(1)) // reanchor() 시점에 evict하는 방식이라 TTL은 크게 의미 없음
                .computePrefixWith(CacheKeyPrefix.simple())
                .serializeKeysWith(fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(fromSerializer(clockSerializer));

        return RedisCacheManager
                .builder(redisConnectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    /**
     * Redis 장애 시 캐시 조회/저장/무효화 실패를 삼켜서 요청이 그대로 DB로 폴백되게 한다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Redis Cache] {} 조회 실패, DB로 폴백합니다. key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("[Redis Cache] {} 저장 실패. key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("[Redis Cache] {} 무효화 실패. key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("[Redis Cache] {} 전체 삭제 실패.", cache.getName(), exception);
            }
        };
    }

    @Bean
    public RedisTemplate<String, QuoteWindowSnapshot> quoteRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        JacksonJsonRedisSerializer<QuoteWindowSnapshot> quoteSerializer =
                new JacksonJsonRedisSerializer<>(objectMapper, QuoteWindowSnapshot.class);

        RedisTemplate<String, QuoteWindowSnapshot> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(quoteSerializer);
        return template;
    }
}
