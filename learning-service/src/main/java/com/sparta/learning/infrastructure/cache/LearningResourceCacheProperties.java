package com.sparta.learning.infrastructure.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 학습 자료 후보 캐시의 운영값 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.resources.cache")
public class LearningResourceCacheProperties {

    private Duration ttl = Duration.ofHours(24);
    private int poolSize = 20;
    private int recentRecommendationDays = 30;
}
