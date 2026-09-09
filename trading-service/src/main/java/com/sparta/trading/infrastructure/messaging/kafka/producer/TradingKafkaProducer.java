package com.sparta.trading.infrastructure.messaging.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * key(=userId)를 지정해 같은 사용자의 이벤트가 같은 파티션으로 가도록 보장한다.
     * Outbox Publisher가 건별 트랜잭션 안에서 결과를 바로 확인해야 하므로 동기로 대기한다.
     */
    public SendResult<String, Object> sendSync(String topic, String key, Object payload, long timeoutSeconds)
            throws Exception {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        SendResult<String, Object> result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        log.info("[Outbox Publisher] Sent to topic [{}] key=[{}] | offset={}",
                topic, key, result.getRecordMetadata().offset());
        return result;
    }

    //아래는 전부 예시 입니다. 상황에 맞게 추가하거나 변경해서 사용해주세요.
    //컨슈머로 받을때, 컨슈머의 토픽을 프로듀서에서 보내준 토픽명과 일치해야 됩니다.


    public void sendMessage(String topic , String key, String message) {
            kafkaTemplate.send(topic, key, message + " " );
    }



    public void send(String topic, Object payload) {
        log.info("[Kafka Producer] Sending event to topic: {}, payload: {}", topic, payload);

        kafkaTemplate.send(topic, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[Kafka Producer] Success sending event to topic [{}] | Offset: {}",
                                topic, result.getRecordMetadata().offset());
                    } else {
                        log.error("[Kafka Producer] Failed sending event to topic [{}]", topic, ex);
                    }
                });
    }

}
