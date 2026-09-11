package com.sparta.learning.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "aiThreadPoolTaskExecutor")
    public Executor aiThreadPoolTaskExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);     // 기본 유지 스레드 수
        executor.setMaxPoolSize(20);     // 최대 스레드 수
        executor.setQueueCapacity(50);   // 대기열 큐 사이즈
        executor.setThreadNamePrefix("AI-Async-");

        Counter rejectedCounter = Counter.builder("learning.ai.executor.rejected")
                .description("AI executor rejected task count")
                .register(meterRegistry);
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            rejectedCounter.increment();
            throw new RejectedExecutionException("AI executor queue is full");
        });

        executor.initialize();

        // 스레드풀의 순간 상태는 Counter가 아닌 Gauge로 노출한다.
        Gauge.builder("learning.ai.executor.active.threads", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active AI executor threads")
                .register(meterRegistry);
        Gauge.builder("learning.ai.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize)
                .description("Current AI executor pool size")
                .register(meterRegistry);
        Gauge.builder(
                        "learning.ai.executor.queue.size",
                        executor,
                        target -> target.getThreadPoolExecutor().getQueue().size()
                )
                .description("Queued AI feedback tasks")
                .register(meterRegistry);
        Gauge.builder(
                        "learning.ai.executor.queue.remaining.capacity",
                        executor,
                        target -> target.getThreadPoolExecutor().getQueue().remainingCapacity()
                )
                .description("Remaining AI executor queue capacity")
                .register(meterRegistry);

        return executor;
    }
}
