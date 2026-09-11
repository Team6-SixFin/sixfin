package com.sparta.learning.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * DLT 리스너 전용 설정.
 * 본문을 해석하지 않고 원본 바이트로 받아 실패 이벤트로 보관하므로 역직렬화가 실패하지 않는다.
 */
@Slf4j
@Configuration
public class DeadLetterKafkaConfig {

    // DB 저장이 실패하면 몇 번만 다시 시도하고, 그 뒤에는 로그만 남기고 넘어간다
    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public ConsumerFactory<String, byte[]> deadLetterConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * 여기서 DeadLetterPublishingRecoverer를 쓰면 -dlt-dlt 토픽으로 다시 전달되므로
     * 재시도를 소진하면 로그만 남기고 offset을 넘긴다. 원본은 DLT 토픽에 그대로 남는다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> deadLetterKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> deadLetterConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(deadLetterConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "DLT 메시지를 실패 이벤트로 보관하지 못했습니다. topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), exception),
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES)
        ));
        return factory;
    }
}
