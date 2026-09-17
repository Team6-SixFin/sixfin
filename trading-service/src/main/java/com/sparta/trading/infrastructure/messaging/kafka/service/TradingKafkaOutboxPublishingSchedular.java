package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.outboxEvents.PendingOutboxEventsRef;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class TradingKafkaOutboxPublishingSchedular {

    private final OutboxEventsQueryRepository outboxEventsQueryRepository;
    private final TradingKafkaOutboxPublisher kafkaOutboxPublisher;
    private final OutboxPublisherProperties outboxPublisherProperties;
    private final Clock clock;
    private final ExecutorService executor;
    private final AtomicLong retryAfterMs = new AtomicLong();

    public TradingKafkaOutboxPublishingSchedular(OutboxEventsQueryRepository outboxEventsQueryRepository, TradingKafkaOutboxPublisher kafkaOutboxPublisher, OutboxPublisherProperties outboxPublisherProperties, Clock clock) {
        this.outboxEventsQueryRepository = outboxEventsQueryRepository;
        this.kafkaOutboxPublisher = kafkaOutboxPublisher;
        this.outboxPublisherProperties = outboxPublisherProperties;
        this.clock = clock;
        this.executor = Executors.newFixedThreadPool(outboxPublisherProperties.parallelism());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    // 쓰레드 풀에 작업 전달
    @Scheduled(fixedDelayString = "500")
    public void publishPending(){
        if (isPaused()) return;
        List<PendingOutboxEventsRef> refs = outboxEventsQueryRepository.findPendingRefs(outboxPublisherProperties.batchSize());
        Map<String, List<PendingOutboxEventsRef>> groupMap =
                refs.stream().collect(Collectors.groupingBy(PendingOutboxEventsRef::partitionKey));

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for(List<PendingOutboxEventsRef> groupList : groupMap.values()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> task(groupList), executor
            );
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();
    }

    private void task(List<PendingOutboxEventsRef> outboxEventsRefs){
        for(PendingOutboxEventsRef ref : outboxEventsRefs) {
            if (isPaused()) return;
            OutboxPublishResult result = kafkaOutboxPublisher.publishOne(ref.id());
            if (result == OutboxPublishResult.RETRY_LATER) {
                // 이벤트는 PENDING에 남겨 두므로 재개 시 같은 사용자의 뒤 이벤트보다 먼저 조회된다.
                retryAfterMs.accumulateAndGet(clock.millis() + outboxPublisherProperties.retryPauseMs(), Math::max);
                return;
            }
            if (result == OutboxPublishResult.RETRY_WITHOUT_PAUSE) return;
        }
    }

    private boolean isPaused() {
        return clock.millis() < retryAfterMs.get();
    }
}
