package com.sparta.trading.infrastructure.messaging.kafka.service;

import com.sparta.trading.domain.repository.outboxEvents.OutboxEventsQueryRepository;
import com.sparta.trading.domain.repository.outboxEvents.PendingOutboxEventsRef;
import com.sparta.trading.infrastructure.messaging.kafka.OutboxPublisherProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
public class TradingKafkaOutboxPublishingSchedular {

    private final OutboxEventsQueryRepository outboxEventsQueryRepository;
    private final TradingKafkaOutboxPublisher kafkaOutboxPublisher;
    private final OutboxPublisherProperties outboxPublisherProperties;
    private final ExecutorService executor;

    public TradingKafkaOutboxPublishingSchedular(OutboxEventsQueryRepository outboxEventsQueryRepository, TradingKafkaOutboxPublisher kafkaOutboxPublisher, OutboxPublisherProperties outboxPublisherProperties) {
        this.outboxEventsQueryRepository = outboxEventsQueryRepository;
        this.kafkaOutboxPublisher = kafkaOutboxPublisher;
        this.outboxPublisherProperties = outboxPublisherProperties;
        this.executor = Executors.newFixedThreadPool(outboxPublisherProperties.parallelism());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    // 쓰레드 풀에 작업 전달
    @Scheduled(fixedDelayString = "3000")
    public void publishPending(){
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

    // 적절한 예외나 반환 값 생각하기
    private void task(List<PendingOutboxEventsRef> outboxEventsRefs){
        for(PendingOutboxEventsRef ref : outboxEventsRefs) {
            boolean publishResult = kafkaOutboxPublisher.publishOne(ref.id());
            if(!publishResult){
                return;
            }
        }
    }
}
