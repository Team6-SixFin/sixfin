package com.sparta.learning.infrastructure.config;

import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncConfigTest {

    @AfterEach
    void clearProviderContext() {
        AiProviderContextHolder.clear();
    }

    @Test
    @DisplayName("AI Executor의 스레드와 대기열 상태를 Gauge로 등록한다")
    void registersAiExecutorGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Executor configuredExecutor = new AsyncConfig()
                .aiThreadPoolTaskExecutor(meterRegistry, new AiExecutorProperties());
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
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig()
                .aiThreadPoolTaskExecutor(meterRegistry, new AiExecutorProperties());

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

    @Test
    @DisplayName("스레드풀 크기를 설정값으로 만든다")
    void buildsExecutorFromProperties() {
        ThreadPoolTaskExecutor executor = executorWith(3, 7, 4);

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaxPoolSize()).isEqualTo(7);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(4);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("큐가 가득 차면 스레드를 core 이상으로 늘린다")
    void growsThreadsBeyondCoreWhenQueueIsFull() throws Exception {
        // core 2 + 큐 1 이므로 3건째부터 큐에 쌓이고, 4건째부터 스레드가 늘어난다.
        ThreadPoolTaskExecutor executor = executorWith(2, 4, 1);
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // 큐 1칸을 포함해 5건을 넣으면 스레드 4개가 모두 일해야 한다.
            for (int i = 0; i < 5; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.getPoolSize()).isEqualTo(4);
            assertThat(executor.getActiveCount()).isEqualTo(4);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("스레드와 큐를 모두 채운 뒤에 들어온 작업은 거부하고 지표로 센다")
    void rejectsAndCountsWhenThreadsAndQueueAreFull() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = executorWith(meterRegistry, 2, 4, 1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // 수용량은 스레드 4 + 큐 1 = 5건이다.
            for (int i = 0; i < 5; i++) {
                executor.execute(() -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(meterRegistry.find("learning.ai.executor.rejected").counter().count()).isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor executorWith(int corePoolSize, int maxPoolSize, int queueCapacity) {
        return executorWith(new SimpleMeterRegistry(), corePoolSize, maxPoolSize, queueCapacity);
    }

    private ThreadPoolTaskExecutor executorWith(SimpleMeterRegistry meterRegistry,
                                                int corePoolSize, int maxPoolSize, int queueCapacity) {
        AiExecutorProperties properties = new AiExecutorProperties();
        properties.setCorePoolSize(corePoolSize);
        properties.setMaxPoolSize(maxPoolSize);
        properties.setQueueCapacity(queueCapacity);

        return (ThreadPoolTaskExecutor) new AsyncConfig()
                .aiThreadPoolTaskExecutor(meterRegistry, properties);
    }
}
