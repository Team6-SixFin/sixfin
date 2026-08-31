package com.sparta.learning.application.service;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.domain.entity.ConsumedEvent;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.messaging.kafka.mapper.TradeEventSnapshotMapper;
import com.sparta.learning.infrastructure.persistence.repository.ClosedPositionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.ConsumedEventRepository;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kafka 이벤트 원본과 그 이벤트에서 만든 스냅샷을 저장하는 수집 파이프라인
 * 진단과 AI 피드백 생성은 이 클래스의 책임이 아니며 후속 서비스에서 스냅샷을 사용
 */
@Service
@RequiredArgsConstructor
public class TradeEventIngestionService {

    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final ConsumedEventRepository consumedEventRepository;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final ClosedPositionSnapshotRepository closedPositionSnapshotRepository;
    private final TradeEventSnapshotMapper snapshotMapper;
    private final Validator validator;

    /**
     * 원본 이벤트와 파생 스냅샷을 하나의 트랜잭션으로 저장
     * 스냅샷 저장이 실패하면 consumed_events 저장도 함께 롤백되어 재처리 가능
     */
    @Transactional
    public EventIngestionResult ingest(TradingEventEnvelope event) {
        validateEnvelope(event);

        // 빠른 중복 확인이며, 동시 소비 경쟁의 최종 방어선은 event_id UNIQUE 제약
        if (consumedEventRepository.existsByEventId(event.eventId())) {
            return EventIngestionResult.DUPLICATE;
        }

        ConsumedEvent consumedEvent = consumedEventRepository.save(ConsumedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .eventVersion(event.eventVersion())
                .userId(event.userId())
                .payload(event.payload())
                .occurredAt(event.occurredAt())
                .build());

        switch (event.eventType()) {
            case BUY_EXECUTED -> executionSnapshotRepository.save(
                    snapshotMapper.toBuySnapshot(consumedEvent, event)
            );
            case SELL_EXECUTED -> executionSnapshotRepository.save(
                    snapshotMapper.toSellSnapshot(consumedEvent, event)
            );
            case POSITION_CLOSED -> closedPositionSnapshotRepository.save(
                    snapshotMapper.toClosedPositionSnapshot(consumedEvent, event)
            );
        }

        return EventIngestionResult.PROCESSED;
    }

    private void validateEnvelope(TradingEventEnvelope event) {
        if (event == null) {
            throw new InvalidTradeEventException("Kafka 이벤트 본문이 없습니다.");
        }

        Set<ConstraintViolation<TradingEventEnvelope>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String invalidFields = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidTradeEventException("유효하지 않은 이벤트 envelope입니다. violations=[" + invalidFields + "]");
        }

        if (event.eventVersion() != SUPPORTED_EVENT_VERSION) {
            throw new InvalidTradeEventException(
                    "지원하지 않는 이벤트 버전입니다. eventId=" + event.eventId()
                            + ", version=" + event.eventVersion()
            );
        }
    }
}
