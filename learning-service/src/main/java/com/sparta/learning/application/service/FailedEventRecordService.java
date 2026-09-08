package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.persistence.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* DLT로 보관된 이벤트를 조회·재처리할 수 있도록 DB에 적재한다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedEventRecordService {

    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    //같은 이벤트가 서로 다른 원인으로 여러 번 실패할 수 있어 중복을 막지 않는다
    @Transactional
    public void record(
            TradingEventEnvelope event,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String failureReason
    ) {
        if (event == null) {
            // 역직렬화 단계에서 실패하면 본문이 없다. (로그로만 알림)
            log.error(
                    "본문이 없는 DLT 메시지라 기록할 수 없습니다. topic={}, offset={}, reason={}",
                    originalTopic, originalOffset, failureReason
            );
            return;
        }

        failedEventRepository.save(FailedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .userId(event.userId())
                // 재처리는 이 원본으로 수집부터 다시 실행하므로 env 전체를 보관
                .payload(objectMapper.valueToTree(event))
                .failureReason(failureReason)
                .originalTopic(originalTopic)
                .originalPartition(originalPartition)
                .originalOffset(originalOffset)
                .build());
    }
}
