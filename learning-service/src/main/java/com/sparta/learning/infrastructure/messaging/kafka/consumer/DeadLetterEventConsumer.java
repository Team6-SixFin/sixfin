package com.sparta.learning.infrastructure.messaging.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.service.FailedEventRecordService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * DLT에 보관된 이벤트를 DB로 옮긴다. 운영자가 조회하고 재처리할 수 있도록 failed_events 테이블에 적재한다.
 * 원본 바이트를 그대로 받아 직접 역직렬화한다. 컨테이너에 맡기면 본문이 깨졌을 때 이 리스너가 다시 실패하고 -dlt-dlt 토픽으로 무한히 전달되는 문제 생김
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterEventConsumer {

    private final FailedEventRecordService failedEventRecordService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${learning.kafka.topics.trade-events}-dlt",
            containerFactory = "deadLetterKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        String rawValue = rawValue(record);
        TradingEventEnvelope event = toEnvelope(rawValue);

        failedEventRecordService.record(
                event,
                rawValue,
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                intHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
                buildFailureReason(record)
        );

        log.info(
                "실패 이벤트를 기록했습니다. eventId={}, originalTopic={}, originalOffset={}",
                event == null ? null : event.eventId(),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC),
                longHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET)
        );
    }

    private String rawValue(ConsumerRecord<String, byte[]> record) {
        return record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8);
    }

    // 본문이 깨졌으면 null을 돌려주고, 보관은 원본 문자열로 이어간다
    private TradingEventEnvelope toEnvelope(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(rawValue, TradingEventEnvelope.class);
        } catch (Exception exception) {
            log.warn("DLT 메시지 본문을 해석하지 못했습니다. 원본만 보관합니다.", exception);
            return null;
        }
    }

    // 예외 클래스와 메시지를 함께 남겨 운영자가 원인을 바로 구분할 수 있게 한다
    private String buildFailureReason(ConsumerRecord<?, ?> record) {
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
