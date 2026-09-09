package com.sparta.learning.infrastructure.config;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/* 재시도로 복구되지 않은 이벤트를 DLT에 보관한다 */
@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 10_000L;
    // 상한이 없으면 무한 재시도가 됨
    private static final long MAX_ELAPSED_TIME_MS = 30_000L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaOperations<String, TradingEventEnvelope> kafkaOperations,
            KafkaOperations<String, byte[]> rawKafkaOperations,
            LearningMetrics learningMetrics
    ) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                deadLetterRecoverer(kafkaOperations, rawKafkaOperations, learningMetrics),
                backOff()
        );

        // 스키마가 안맞는 이벤트는 재시도 결과가 동일하므로 재시도 없이 바로 DLT로
        errorHandler.addNotRetryableExceptions(InvalidTradeEventException.class);

        return errorHandler;
    }

    /**
     * 역직렬화에 실패하면 원본 바이트가 그대로 발행된다.
     * JsonSerializer로 보내면 byte[]가 base64 문자열로 감싸져 DLT에서 원본을 읽을 수 없으므로
     * 이 경우에만 ByteArraySerializer 템플릿을 쓴다.
     */
    @Bean
    public KafkaOperations<String, byte[]> rawKafkaOperations(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    // 원본 토픽/파티션/offset과 예외 정보는 헤더에 자동으로 담김
    DeadLetterPublishingRecoverer deadLetterRecoverer(
            KafkaOperations<String, TradingEventEnvelope> kafkaOperations,
            KafkaOperations<String, byte[]> rawKafkaOperations,
            LearningMetrics learningMetrics
    ) {
        // 값 타입별 템플릿. byte[]가 먼저 와야 원본 바이트가 base64로 감싸지지 않는다
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawKafkaOperations);
        templates.put(Object.class, kafkaOperations);

        return new DeadLetterPublishingRecoverer(templates) {
            @Override
            public void accept(ConsumerRecord<?, ?> record, Exception exception) {
                super.accept(record, exception);
                recordDeadLetter(record, exception, learningMetrics);
            }
        };
    }

    // DLT 발행은 진단 또는 수집이 누락됐다는 뜻이므로 로그와 메트릭을 함께 남긴다
    private void recordDeadLetter(ConsumerRecord<?, ?> record, Exception exception, LearningMetrics learningMetrics) {
        TradingEventEnvelope event = record.value() instanceof TradingEventEnvelope envelope ? envelope : null;

        log.error(
                "재시도를 소진해 DLT로 보냈습니다. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                event == null ? null : event.eventId(),
                event == null ? null : event.eventType(),
                record.topic(),
                record.partition(),
                record.offset(),
                exception
        );

        learningMetrics.recordDeadLetter(event == null ? null : event.eventType(), exception);
    }

    ExponentialBackOff backOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_INTERVAL_MS, MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_TIME_MS);
        return backOff;
    }
}
