package com.sparta.learning.application.facade;

import com.sparta.learning.application.diagnosis.DiagnosisService;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 이벤트 수집과 진단의 실행 순서, 진단 실패 처리를 검증합니다.
@ExtendWith(MockitoExtension.class)
class TradeEventFacadeTest {

    @Mock
    private TradeEventIngestionService ingestionService;

    @Mock
    private DiagnosisService diagnosisService;

    @InjectMocks
    private TradeEventFacade facade;

    private final TradingEventEnvelope event = mock(TradingEventEnvelope.class);

    // 체결 이벤트는 스냅샷 저장 후 진단까지 이어져야 한다
    @Test
    void 체결_이벤트는_수집_후_진단을_실행한다() {
        ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
        when(ingestionService.ingest(event)).thenReturn(IngestionResult.processed(snapshot));

        facade.handle(event);

        verify(diagnosisService).diagnose(snapshot);
    }

    // 이미 처리한 이벤트는 진단도 저장되어 있으므로 다시 실행하지 않는다
    @Test
    void 중복_이벤트는_진단을_실행하지_않는다() {
        when(ingestionService.ingest(event)).thenReturn(IngestionResult.duplicate());

        facade.handle(event);

        verifyNoInteractions(diagnosisService);
    }

    // 포지션 종료는 ClosedPositionSnapshot이라 현재 진단 인터페이스로 처리할 수 없다
    @Test
    void 진단_대상이_아니면_진단을_실행하지_않는다() {
        when(ingestionService.ingest(event)).thenReturn(IngestionResult.processedWithoutDiagnosis());

        facade.handle(event);

        verifyNoInteractions(diagnosisService);
    }

    // 진단 실패가 Consumer까지 전파되면 offset이 커밋되지 않아 이벤트를 재처리하게 된다
    // 스냅샷은 이미 커밋되었으므로 예외를 여기서 차단한다
    @Test
    void 진단이_실패해도_예외를_밖으로_던지지_않는다() {
        ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
        when(snapshot.getExecutionId()).thenReturn(UUID.randomUUID());
        when(ingestionService.ingest(event)).thenReturn(IngestionResult.processed(snapshot));
        when(diagnosisService.diagnose(any(ExecutionSnapshot.class)))
                .thenThrow(new IllegalStateException("진단 실패"));

        assertThatCode(() -> facade.handle(event)).doesNotThrowAnyException();
    }

    // 수집 실패는 스냅샷이 저장되지 않은 상태이므로 재처리가 필요하다
    @Test
    void 수집이_실패하면_예외를_그대로_전파한다() {
        when(ingestionService.ingest(event)).thenThrow(new IllegalStateException("수집 실패"));

        assertThatCode(() -> facade.handle(event))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(diagnosisService);
    }

    // Consumer가 로그에 사용하므로 수집 결과를 그대로 반환해야 한다
    @Test
    void 수집_결과를_그대로_반환한다() {
        ExecutionSnapshot snapshot = mock(ExecutionSnapshot.class);
        IngestionResult expected = IngestionResult.processed(snapshot);
        when(ingestionService.ingest(event)).thenReturn(expected);
        when(diagnosisService.diagnose(snapshot)).thenReturn(List.of());

        IngestionResult result = facade.handle(event);

        assertThat(result).isSameAs(expected);
    }
}
