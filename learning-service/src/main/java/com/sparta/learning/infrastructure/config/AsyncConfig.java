package com.sparta.learning.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "aiThreadPoolTaskExecutor")
    public Executor aiThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);     // 기본 유지 스레드 수
        executor.setMaxPoolSize(20);     // 최대 스레드 수
        executor.setQueueCapacity(50);   // 대기열 큐 사이즈
        executor.setThreadNamePrefix("AI-Async-");
        executor.initialize();
        return executor;
    }
}