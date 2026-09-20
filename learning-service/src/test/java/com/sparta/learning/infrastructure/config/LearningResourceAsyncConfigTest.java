package com.sparta.learning.infrastructure.config;

import com.sparta.learning.application.content.LearningResourceLinker;
import com.sparta.learning.domain.entity.Feedback;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LearningResourceAsyncConfigTest {

    @Test
    void 학습_자료_스레드풀_크기를_설정값으로_만든다() {
        ThreadPoolTaskExecutor executor = executorWith(new SimpleMeterRegistry(), 3, 6, 7);

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaxPoolSize()).isEqualTo(6);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(7);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 포화되면_예외를_던지지_않고_버린_뒤_지표로_센다() throws Exception {
        // 제출하는 쪽이 AI 워커 스레드다. 여기서 예외가 나가면
        // 이미 완료된 AI 피드백 처리에 예외가 튄다.
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = executorWith(meterRegistry, 1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // 스레드 1 + 큐 1 = 수용량 2
            for (int i = 0; i < 2; i++) {
                executor.execute(() -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertThatCode(() -> executor.execute(() -> { })).doesNotThrowAnyException();
            assertThat(meterRegistry.find("learning.resource.executor.rejected").counter().count())
                    .isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void 학습_자료_연결이_가리키는_executor_이름이_실제_빈_이름과_같다() throws Exception {
        // 이름이 어긋나면 Spring이 조용히 기본 executor로 흘려보내
        // 분리 작업이 아무 효과 없이 통과한다.
        String beanName = LearningResourceAsyncConfig.class
                .getMethod("learningResourceTaskExecutor", MeterRegistry.class, LearningResourceExecutorProperties.class)
                .getAnnotation(Bean.class)
                .name()[0];
        String qualifier = LearningResourceLinker.class
                .getMethod("linkAsync", Feedback.class)
                .getAnnotation(Async.class)
                .value();

        assertThat(qualifier).isEqualTo(beanName);
    }

    private ThreadPoolTaskExecutor executorWith(SimpleMeterRegistry meterRegistry,
                                                int corePoolSize, int maxPoolSize, int queueCapacity) {
        LearningResourceExecutorProperties properties = new LearningResourceExecutorProperties();
        properties.setCorePoolSize(corePoolSize);
        properties.setMaxPoolSize(maxPoolSize);
        properties.setQueueCapacity(queueCapacity);

        return (ThreadPoolTaskExecutor) new LearningResourceAsyncConfig()
                .learningResourceTaskExecutor(meterRegistry, properties);
    }
}
