package com.sparta.learning.application.service;

import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.model.IngestionResult;
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
     *
     * 진단은 이 트랜잭션에 포함하지 않는다.
     * 진단 규칙에서 예외가 발생하면 스냅샷 저장까지 롤백되고,
     * Kafka 재시도 횟수를 넘기면 이벤트 자체가 유실되기 때문이다.
     * 스냅샷은 Trading 이벤트의 사본이라 유실되면 복구가 어렵지만,
     * 진단은 스냅샷만 있으면 언제든 다시 실행할 수 있다.
     *
     * 그래서 저장한 스냅샷을 반환하고, 진단 호출 여부와 순서는 호출하는 쪽이 결정한다.
     */
    @Transactional
    public IngestionResult ingest(TradingEventEnvelope event) {
        validateEnvelope(event);

        // 빠른 중복 확인이며, 동시 소비 경쟁의 최종 방어선은 event_id UNIQUE 제약
        // 이미 처리한 이벤트는 진단도 저장되어 있으므로 스냅샷을 반환하지 않는다
        if (consumedEventRepository.existsByEventId(event.eventId())) {
            return IngestionResult.duplicate();
        }

        ConsumedEvent consumedEvent = consumedEventRepository.save(ConsumedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .eventVersion(event.eventVersion())
                .userId(event.userId())
                .payload(event.payload())
                .occurredAt(event.occurredAt())
                .build());

        return switch (event.eventType()) {
            case BUY_EXECUTED -> IngestionResult.processed(
                    executionSnapshotRepository.save(
                            snapshotMapper.toBuySnapshot(consumedEvent, event))
            );
            case SELL_EXECUTED -> IngestionResult.processed(
                    executionSnapshotRepository.save(
                            snapshotMapper.toSellSnapshot(consumedEvent, event))
            );
            // 포지션 종료는 현재 진단 인터페이스로 처리할 수 없다. CLOSE 규칙 구현 시 같이 정리하겠습니다.
            case POSITION_CLOSED -> {
                closedPositionSnapshotRepository.save(
                        snapshotMapper.toClosedPositionSnapshot(consumedEvent, event));
                yield IngestionResult.processedWithoutDiagnosis();
            }
        };
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
