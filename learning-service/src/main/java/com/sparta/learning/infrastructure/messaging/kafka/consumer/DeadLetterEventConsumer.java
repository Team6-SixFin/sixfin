package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.sparta.learning.application.service.FailedEventRecordService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/* DLT에 보관된 이벤트를 DB로 옮긴다. 운영자가 조회하고 재처리할 수 있도록 failed_events 테이블에 적재한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterEventConsumer {

    private final FailedEventRecordService failedEventRecordService;

    @KafkaListener(topics = "${learning.kafka.topics.trade-events}-dlt")
    public void consume(ConsumerRecord<String, TradingEventEnvelope> record) {
        failedEventRecordService.record(
                record.value(),
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

    // 헤더는 문자열로 실려오는 경우와 4바이트 정수로 실려오는 경우가 있다
    private Integer intHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        byte[] value = header.value();
        if (value.length == Integer.BYTES) {
            return java.nio.ByteBuffer.wrap(value).getInt();
        }
        return parseInt(new String(value, StandardCharsets.UTF_8));
    }

    private Long longHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        byte[] value = header.value();
        if (value.length == Long.BYTES) {
            return java.nio.ByteBuffer.wrap(value).getLong();
        }
        if (value.length == Integer.BYTES) {
            return (long) java.nio.ByteBuffer.wrap(value).getInt();
        }
        Integer parsed = parseInt(new String(value, StandardCharsets.UTF_8));
        return parsed == null ? null : parsed.longValue();
    }

    // 헤더 형식이 바뀌어도 적재 자체는 멈추지 않게 파싱 실패는 null로 둔다
    private Integer parseInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("DLT 헤더 숫자 변환에 실패했습니다. value={}", value);
            return null;
        }
    }
}
