package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningKafkaConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    //아래는 전부 예시 입니다. 상황에 맞게 추가하거나 변경해서 사용해주세요.
    //프로듀서에서 지정한 토픽 이름과 정확히 일치해야 메시지를 수신할 수 있습니다.

    // 이 메서드는 Kafka에서 메시지를 소비하는 리스너 메서드입니다.
    // @KafkaListener 어노테이션은 이 메서드를 Kafka 리스너로 설정합니다.
    @KafkaListener(groupId = "group_a", topics = "topic1")
    // Kafka 토픽 "test-topic"에서 메시지를 수신하면 이 메서드가 호출됩니다.
    // groupId는 컨슈머 그룹을 지정하여 동일한 그룹에 속한 다른 컨슈머와 메시지를 분배받습니다.
    public void consumeFromGroupA(String message) {
        log.info("Group A consumed message from topic1: " + message);
    }


    @KafkaListener(groupId = "group_a", topics = "topic2")
    public void consumeTradingEvent(@Payload Object eventDto) {
        log.info("[Kafka Consumer] Received Trading Event: {}", eventDto);

        try {
            // TODO: 수신된 데이터를 바탕으로 러닝 서비스 비즈니스 로직 수행
            // learningApplicationService.processLearningData(eventDto);
            log.info("[Kafka Consumer] Successfully processed event");
        } catch (Exception e) {
            log.error("[Kafka Consumer] Error processing trading event", e);
            // 필요 시 재시도(Retry) 또는 DLT(Dead Letter Topic) 처리 로직 연동
        }
    }


}
