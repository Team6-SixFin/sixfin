package com.sparta.learning.infrastructure.config;

import com.sparta.learning.infrastructure.ai.AiProviderContextHolder;
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

        // ===== 성능 측정용 AI provider 오버라이드 전달 =====
        //
        // 제출 스레드(Tomcat)의 provider 오버라이드를 @Async 워커 스레드로 넘긴다.
        // TaskDecorator.decorate() 는 executor.execute() 호출 시점,
        // 즉 '제출한 스레드'에서 실행되므로 여기서 캡처한 값이 곧 그 요청의 provider 다.
        //
        // 이 장치가 없으면 워커에서 ThreadLocal 이 비어 있어
        // 헤더를 보내도 항상 기본 provider(Gemini)로 처리된다.
        //
        // ThreadPoolTaskExecutor 는 initialize() 시점에 내부 ThreadPoolExecutor 를
        // 만들면서 데코레이터를 반영하므로, initialize() 뒤에 두면 조용히 무시된다.
        executor.setTaskDecorator(runnable -> {
            String provider = AiProviderContextHolder.get();
            return () -> {
                if (provider != null) {
                    AiProviderContextHolder.set(provider);
                }
                try {
                    runnable.run();
                } finally {
                    // 워커 스레드도 재사용되므로 반드시 정리한다.
                    AiProviderContextHolder.clear();
                }
            };
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
