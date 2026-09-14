package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.sparta.learning.application.service.FailedEventRecordService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/* DLT에 보관된 이벤트를 DB로 옮긴다. 운영자가 조회하고 재처리할 수 있도록 failed_events 테이블에 적재한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterEventConsumer {

    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(DeadLetterEventConsumer.class);

    private final FailedEventRecordService failedEventRecordService;

    @KafkaListener(topics = "${learning.kafka.topics.trade-events}-dlt")
    public void consume(ConsumerRecord<String, TradingEventEnvelope> record) {
        failedEventRecordService.record(
                record.value(),
                rawValue(record),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                buildFailureReason(record)
        );

        log.info(
                "실패 이벤트를 기록했습니다. eventId={}, originalTopic={}, originalOffset={}",
                record.value() == null ? null : record.value().eventId(),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET)
        );
    }

    /**
     * 역직렬화에 실패하면 ErrorHandlingDeserializer가 value를 null로 넘기고
     * 원본 바이트를 예외 헤더에 담아둔다. 그 바이트를 꺼내 보관해야 유실되지 않는다.
     */
    private String rawValue(ConsumerRecord<String, TradingEventEnvelope> record) {
        if (record.value() != null) {
            return null;
        }

        DeserializationException exception = SerializationUtils.getExceptionFromHeader(
                record, KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG_ACCESSOR);
        if (exception == null || exception.getData() == null) {
            return null;
        }
        return new String(exception.getData(), StandardCharsets.UTF_8);
    }

    // 예외 클래스와 메시지를 함께 남겨 운영자가 원인을 바로 구분할 수 있게 한다
    private String buildFailureReason(ConsumerRecord<String, TradingEventEnvelope> record) {
        String type = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String message = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        if (type == null && message == null) {
            return null;
        }
        return type == null ? message : type + ": " + message;
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Integer intHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : ByteBuffer.wrap(header.value()).getInt();
    }

    private Long longHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : ByteBuffer.wrap(header.value()).getLong();
    }
}
