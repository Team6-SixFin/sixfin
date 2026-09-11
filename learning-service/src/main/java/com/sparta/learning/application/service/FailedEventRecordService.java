package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            String rawValue,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String failureReason
    ) {
        // 역직렬화에 실패하면 eventId/eventType을 모르지만, 보관하지 않으면 offset이 커밋되어 유실된다
        if (event == null) {
            log.error(
                    "본문을 해석할 수 없는 DLT 메시지를 원본만 보관합니다. topic={}, partition={}, offset={}, reason={}",
                    originalTopic, originalPartition, originalOffset, failureReason
            );
            failedEventRepository.save(FailedEvent.builder()
                    .payload(unparsedPayload(rawValue))
                    .failureReason(failureReason)
                    .originalTopic(originalTopic)
                    .originalPartition(originalPartition)
                    .originalOffset(originalOffset)
                    .build());
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

    /** payload는 NOT NULL이고 재처리 API가 이 값을 TradingEventEnvelope로 복원한다.
     * 해석하지 못한 원본은 복원 대상이 아니므로 감싸서 저장하고, 재처리 시 422로 걸러지게 둔다.
     */
    private ObjectNode unparsedPayload(String rawValue) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("_unparsed", true);
        payload.put("_raw", rawValue);
        return payload;
    }
}
