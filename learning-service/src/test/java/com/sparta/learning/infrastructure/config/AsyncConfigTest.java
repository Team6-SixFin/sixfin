package com.sparta.learning.infrastructure.config;

import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @AfterEach
    void clearProviderContext() {
        AiProviderContextHolder.clear();
    }

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

    @Test
    @DisplayName("요청 스레드의 AI provider를 비동기 작업에 전달하고 작업 후 제거한다")
    void propagatesAndClearsAiProviderContext() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                new AsyncConfig().aiThreadPoolTaskExecutor(meterRegistry);

        try {
            AiProviderContextHolder.set("stub");
            CompletableFuture<String> propagated = new CompletableFuture<>();
            executor.execute(() -> propagated.complete(AiProviderContextHolder.get()));
            AiProviderContextHolder.clear();

            assertThat(propagated.get(2, TimeUnit.SECONDS)).isEqualTo("stub");

            CompletableFuture<String> nextRequest = new CompletableFuture<>();
            executor.execute(() -> nextRequest.complete(AiProviderContextHolder.get()));

            assertThat(nextRequest.get(2, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdown();
        }
    }
}
