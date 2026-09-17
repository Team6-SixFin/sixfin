package com.sparta.trading.infrastructure.messaging.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * key(=userId)를 지정해 같은 사용자의 이벤트가 같은 파티션으로 가도록 보장한다.
     * Kafka producer의 delivery.timeout.ms로 완료 시점이 제한된다. 완료 전에 자체
     * 타임아웃을 내면 전송 중인 레코드를 실패로 오인해 다음 이벤트가 앞지를 수 있다.
     */
    public SendResult<String, Object> sendSync(String topic, String key, Object payload)
            throws Exception {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        SendResult<String, Object> result = future.get();
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
