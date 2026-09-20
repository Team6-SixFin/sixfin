package com.sparta.learning.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 학습 자료 연결 전용 스레드풀 크기 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "learning.resources.executor")
public class LearningResourceExecutorProperties {

    // 외부 검색은 대기가 대부분이라 AI 풀보다 작게 시작한다
    private int corePoolSize = 2;

    private int maxPoolSize = 8;

    private int queueCapacity = 20;
}
