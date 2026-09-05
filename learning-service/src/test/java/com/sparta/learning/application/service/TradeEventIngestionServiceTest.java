package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.learning.application.exception.InvalidTradeEventException;
import com.sparta.learning.application.model.EventIngestionResult;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ConsumedEvent;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.messaging.kafka.mapper.TradeEventSnapshotMapper;
import com.sparta.learning.infrastructure.persistence.repository.ClosedPositionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.ConsumedEventRepository;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 이벤트 수집 서비스의 멱등 처리와 이벤트 유형별 저장 분기를 검증
 */
@ExtendWith(MockitoExtension.class)
class TradeEventIngestionServiceTest {

    @Mock
    private ConsumedEventRepository consumedEventRepository;

    @Mock
    private ExecutionSnapshotRepository executionSnapshotRepository;

    @Mock
    private ClosedPositionSnapshotRepository closedPositionSnapshotRepository;

    @Mock
    private TradeEventSnapshotMapper snapshotMapper;

    private ObjectMapper objectMapper;
    private ValidatorFactory validatorFactory;
    private TradeEventIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ingestionService = new TradeEventIngestionService(
                consumedEventRepository,
                executionSnapshotRepository,
                closedPositionSnapshotRepository,
                snapshotMapper,
                validatorFactory.getValidator()
        );
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    // 이미 저장된 체결 이벤트는 스냅샷을 새로 만들지 않고 기존 스냅샷을 재진단 대상으로 반환한다
    @Test
    void returnsExistingSnapshotForAlreadyConsumedExecutionEvent() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
        when(consumedEventRepository.existsByEventId(event.eventId())).thenReturn(true);
        when(executionSnapshotRepository.findByConsumedEventEventId(event.eventId()))
                .thenReturn(Optional.of(snapshot));

        IngestionResult result = ingestionService.ingest(event);

        assertThat(result.status()).isEqualTo(EventIngestionResult.DUPLICATE);
        assertThat(result.executionSnapshot()).isSameAs(snapshot);
        assertThat(result.hasDiagnosisTarget()).isTrue();
        verify(consumedEventRepository, never()).save(any());
        verify(executionSnapshotRepository, never()).save(any());
        verifyNoInteractions(snapshotMapper, closedPositionSnapshotRepository);
    }

    // 소비 이력과 체결 스냅샷의 정합성이 깨졌다면 진단을 누락한 채 정상 처리하지 않는다
    @Test
    void failsWhenConsumedExecutionEventHasNoSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        when(consumedEventRepository.existsByEventId(event.eventId())).thenReturn(true);
        when(executionSnapshotRepository.findByConsumedEventEventId(event.eventId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.ingest(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(event.eventId().toString());

        verify(consumedEventRepository, never()).save(any());
        verify(executionSnapshotRepository, never()).save(any());
        verifyNoInteractions(snapshotMapper, closedPositionSnapshotRepository);
    }

    // 종료 포지션은 CLOSE 진단 구현 전까지 중복 수신 시 재진단 대상으로 반환하지 않는다
    @Test
    void skipsAlreadyConsumedPositionClosedEvent() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/position-closed.json");
        when(consumedEventRepository.existsByEventId(event.eventId())).thenReturn(true);

        IngestionResult result = ingestionService.ingest(event);

        assertThat(result.status()).isEqualTo(EventIngestionResult.DUPLICATE);
        assertThat(result.hasDiagnosisTarget()).isFalse();
        verify(consumedEventRepository, never()).save(any());
        verifyNoInteractions(snapshotMapper, executionSnapshotRepository, closedPositionSnapshotRepository);
    }

    // BUY_EXECUTED 수신 시 원본 이벤트와 체결 스냅샷이 각각 한 번 저장되는지 확인
    @Test
    void savesConsumedEventAndBuyExecutionSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/buy-executed-first.json");
        ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
        when(consumedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(consumedEventRepository.save(any(ConsumedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotMapper.toBuySnapshot(any(ConsumedEvent.class), any(TradingEventEnvelope.class)))
                .thenReturn(snapshot);
        when(executionSnapshotRepository.save(snapshot)).thenReturn(snapshot);

        IngestionResult result = ingestionService.ingest(event);

        assertThat(result.status()).isEqualTo(EventIngestionResult.PROCESSED);
        // 후속 진단에 넘길 수 있도록 저장된 스냅샷을 그대로 담아야 한다
        assertThat(result.executionSnapshot()).isSameAs(snapshot);
        verify(consumedEventRepository).save(any(ConsumedEvent.class));
        verify(executionSnapshotRepository).save(snapshot);
        verifyNoInteractions(closedPositionSnapshotRepository);
    }

    // POSITION_CLOSED 수신 시 체결 스냅샷이 아닌 종료 포지션 스냅샷으로 저장되는지 확인
    @Test
    void savesClosedPositionSnapshot() throws Exception {
        TradingEventEnvelope event = readEnvelope("events/position-closed.json");
        ClosedPositionSnapshot snapshot = mock(ClosedPositionSnapshot.class);
        when(consumedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(consumedEventRepository.save(any(ConsumedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotMapper.toClosedPositionSnapshot(any(ConsumedEvent.class), any(TradingEventEnvelope.class)))
                .thenReturn(snapshot);

        IngestionResult result = ingestionService.ingest(event);

        assertThat(result.status()).isEqualTo(EventIngestionResult.PROCESSED);
        // 포지션 종료는 ClosedPositionSnapshot이라 현재 진단 인터페이스로 처리할 수 없다
        assertThat(result.hasDiagnosisTarget()).isFalse();
        verify(closedPositionSnapshotRepository).save(snapshot);
        verifyNoInteractions(executionSnapshotRepository);
    }

    // 지원 버전이 아닌 이벤트는 DB에 기록하기 전에 거부하는지 확인
    @Test
    void rejectsUnsupportedEventVersion() throws Exception {
        TradingEventEnvelope sample = readEnvelope("events/buy-executed-first.json");
        TradingEventEnvelope unsupported = new TradingEventEnvelope(
                sample.eventId(),
                sample.eventType(),
                2,
                sample.occurredAt(),
                sample.userId(),
                sample.payload()
        );

        assertThatThrownBy(() -> ingestionService.ingest(unsupported))
                .isInstanceOf(InvalidTradeEventException.class)
                .hasMessageContaining("지원하지 않는 이벤트 버전");

        verifyNoInteractions(
                consumedEventRepository,
                executionSnapshotRepository,
                closedPositionSnapshotRepository,
                snapshotMapper
        );
    }

    private TradingEventEnvelope readEnvelope(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, TradingEventEnvelope.class);
        }
    }
}
