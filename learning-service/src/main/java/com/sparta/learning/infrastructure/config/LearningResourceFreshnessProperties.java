package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 외부 검색 자료를 다시 확인할 주기를 자료 유형별로 관리합니다. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.resources.freshness")
public class LearningResourceFreshnessProperties {

    private Duration videoTtl = Duration.ofDays(7);
    private Duration documentTtl = Duration.ofDays(30);
}
