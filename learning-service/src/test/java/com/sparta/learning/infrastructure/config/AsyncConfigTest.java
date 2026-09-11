package com.sparta.learning.infrastructure.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    @DisplayName("AI Executor의 스레드와 대기열 상태를 Gauge로 등록한다")
    void registersAiExecutorGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Executor configuredExecutor = new AsyncConfig().aiThreadPoolTaskExecutor(meterRegistry);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuredExecutor;

        try {
            assertThat(meterRegistry.find("learning.ai.executor.active.threads").gauge()).isNotNull();
            assertThat(meterRegistry.find("learning.ai.executor.pool.size").gauge()).isNotNull();
            assertThat(meterRegistry.find("learning.ai.executor.queue.size").gauge()).isNotNull();
            assertThat(meterRegistry.find("learning.ai.executor.queue.remaining.capacity").gauge()).isNotNull();
            assertThat(meterRegistry.find("learning.ai.executor.rejected").counter()).isNotNull();
        } finally {
            executor.shutdown();
        }
    }
}
