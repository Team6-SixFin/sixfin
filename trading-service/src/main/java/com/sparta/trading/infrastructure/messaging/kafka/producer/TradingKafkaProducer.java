package com.sparta.trading.infrastructure.messaging.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

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
