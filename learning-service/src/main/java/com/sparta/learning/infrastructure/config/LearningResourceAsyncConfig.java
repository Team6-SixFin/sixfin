package com.sparta.learning.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 학습 자료 연결 전용 스레드풀 */
@Slf4j
@Configuration
public class LearningResourceAsyncConfig {

    @Bean(name = "learningResourceTaskExecutor")
    public Executor learningResourceTaskExecutor(MeterRegistry meterRegistry,
                                                 LearningResourceExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("Learning-Resource-");

        Counter rejectedCounter = Counter.builder("learning.resource.executor.rejected")
                .description("Rejected learning resource linking tasks")
                .register(meterRegistry);

        // AI 풀과 달리 예외를 던지지 않고 버린다.
        // 대신 얼마나 버렸는지는 카운터로 남긴다.
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            rejectedCounter.increment();
            log.warn("학습 자료 연결 작업이 포화로 거부되었습니다. 피드백 본문은 유지됩니다.");
        });

        executor.initialize();

        Gauge.builder("learning.resource.executor.active.threads", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active learning resource threads")
                .register(meterRegistry);
        Gauge.builder("learning.resource.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("Current learning resource pool size")
                .register(meterRegistry);
        Gauge.builder(
                        "learning.resource.executor.queue.size",
                        executor,
                        target -> target.getThreadPoolExecutor().getQueue().size()
                )
                .description("Queued learning resource tasks")
                .register(meterRegistry);

        return executor;
    }
}
