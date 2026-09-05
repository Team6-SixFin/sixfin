package com.sparta.learning.application.diagnosis;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.domain.rule.DiagnosisRule;
import com.sparta.learning.domain.rule.StopLossSetRule;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//진단 규칙 실행과 저장을 검증합니다.
class DiagnosisServiceTest {

    private DiagnosisResultRepository diagnosisResultRepository;
    private DiagnosisService diagnosisService;
    private SimpleMeterRegistry meterRegistry;
    private LearningMetrics learningMetrics;

    @BeforeEach
    void setUp() {
        diagnosisResultRepository = mock(DiagnosisResultRepository.class);

        // 저장 요청이 오면 받은 목록을 그대로 돌려준다
        when(diagnosisResultRepository.saveAll(anyCollection()))
                .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
        // 기본은 아직 저장된 진단이 없는 상태
        when(diagnosisResultRepository.findByDiagnosisKeyIn(anyCollection()))
                .thenReturn(List.of());

        meterRegistry = new SimpleMeterRegistry();
        learningMetrics = new LearningMetrics(meterRegistry);
        diagnosisService = new DiagnosisService(
                List.of(new StopLossSetRule()),
                diagnosisResultRepository,
                learningMetrics
        );
    }

    // 최초 매수는 ENTRY 단계이므로 ENTRY 규칙이 실행되고 결과가 저장되어야 한다.
    @Test
    void 최초_매수는_ENTRY_규칙을_실행하고_저장한다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        List<DiagnosisResult> results = diagnosisService.diagnose(snapshot);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getRuleCode()).isEqualTo(RuleCode.STOP_LOSS_SET.name());
        assertThat(results.getFirst().getDiagnosisPhase()).isEqualTo(DiagnosisPhase.ENTRY);
        verify(diagnosisResultRepository).saveAll(anyCollection());
        assertThat(meterRegistry.get("learning.diagnosis.runs")
                .tag("phase", "ENTRY")
                .tag("result", "SUCCESS")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("learning.diagnosis.results")
                .tag("phase", "ENTRY")
                .tag("rule_code", "STOP_LOSS_SET")
                .tag("result", "PASS")
                .counter()
                .count()).isEqualTo(1.0);
    }

    // 추가 매수는 TRADE 단계이므로 ENTRY 규칙이 실행되면 안 된다.
    // 계획 손절가는 최초 매수 시 1회 기록 후 변경할 수 없어 다시 판정할 의미가 없다.
    @Test
    void 추가_매수는_ENTRY_규칙을_실행하지_않는다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.additionalBuy();

        List<DiagnosisResult> results = diagnosisService.diagnose(snapshot);

        assertThat(results).isEmpty();
        verify(diagnosisResultRepository, never()).saveAll(anyCollection());
    }

    // 매도도 TRADE 단계이므로 ENTRY 규칙 대상이 아니다.
    @Test
    void 매도는_ENTRY_규칙을_실행하지_않는다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.sell();

        List<DiagnosisResult> results = diagnosisService.diagnose(snapshot);

        assertThat(results).isEmpty();
        verify(diagnosisResultRepository, never()).saveAll(anyCollection());
    }

    // Kafka 이벤트가 재처리되면 같은 diagnosis_key가 다시 만들어진다.
    // UNIQUE 제약 위반으로 트랜잭션이 롤백되지 않도록 저장 전에 걸러내야 한다.
    @Test
    void 이미_진단된_체결은_다시_저장하지_않는다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();
        DiagnosisResult alreadySaved = new StopLossSetRule().diagnose(snapshot);

        when(diagnosisResultRepository.findByDiagnosisKeyIn(anyCollection()))
                .thenReturn(List.of(alreadySaved));

        List<DiagnosisResult> results = diagnosisService.diagnose(snapshot);

        assertThat(results).isEmpty();
        verify(diagnosisResultRepository, never()).saveAll(anyCollection());
    }

    // 일부만 저장되어 있으면 나머지는 저장되어야 한다.
    // 규칙이 추가되는 도중에 재처리가 일어나는 상황을 가정한다.
    @Test
    void 저장되지_않은_진단만_저장한다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        // 이 서비스에는 규칙이 하나뿐이므로, 다른 키가 저장돼 있어도 걸러지지 않아야 한다
        DiagnosisResult otherRuleResult = new StopLossSetRule()
                .diagnose(ExecutionSnapshotFixture.firstBuyWithStopLoss());
        when(diagnosisResultRepository.findByDiagnosisKeyIn(anyCollection()))
                .thenReturn(List.of(otherRuleResult));

        List<DiagnosisResult> results = diagnosisService.diagnose(snapshot);

        assertThat(results).hasSize(1);
        verify(diagnosisResultRepository).saveAll(anyCollection());
    }

    // 실행할 규칙이 없으면 저장시도를 하지 않아야 한다.
    // 아직 구현하지 않은 단계의 이벤트가 들어오는 경우를 가정
    @Test
    void 실행할_규칙이_없으면_저장하지_않는다() {
        DiagnosisService emptyRuleService = new DiagnosisService(
                List.of(), diagnosisResultRepository, learningMetrics
        );

        List<DiagnosisResult> results = emptyRuleService.diagnose(
                ExecutionSnapshotFixture.firstBuyWithStopLoss()
        );

        assertThat(results).isEmpty();
        verify(diagnosisResultRepository, never()).saveAll(anyCollection());
    }

    // supports()가 false를 반환하면 진단을 실행x
    @Test
    void supports가_false면_규칙을_실행하지_않는다() {
        DiagnosisRule neverSupports = mock(DiagnosisRule.class);
        when(neverSupports.getRuleCode()).thenReturn(RuleCode.STOP_LOSS_SET);
        when(neverSupports.supports(any())).thenReturn(false);

        DiagnosisService service = new DiagnosisService(
                List.of(neverSupports), diagnosisResultRepository, learningMetrics
        );

        List<DiagnosisResult> results = service.diagnose(
                ExecutionSnapshotFixture.firstBuyWithStopLoss()
        );

        assertThat(results).isEmpty();
        verify(neverSupports, never()).diagnose(any());
    }

    // 규칙 실행 중 예외가 발생하면 실패 메트릭을 남기고 예외를 그대로 전파해야 한다.
    @Test
    void 진단_실패는_메트릭을_남기고_예외를_전파한다() {
        DiagnosisRule failingRule = mock(DiagnosisRule.class);
        when(failingRule.getRuleCode()).thenReturn(RuleCode.STOP_LOSS_SET);
        when(failingRule.supports(any())).thenReturn(true);
        when(failingRule.diagnose(any())).thenThrow(new IllegalStateException("진단 실패"));

        DiagnosisService service = new DiagnosisService(
                List.of(failingRule), diagnosisResultRepository, learningMetrics
        );

        assertThatThrownBy(() -> service.diagnose(ExecutionSnapshotFixture.firstBuyWithStopLoss()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진단 실패");

        assertThat(meterRegistry.get("learning.diagnosis.runs")
                .tag("phase", "ENTRY")
                .tag("result", "FAILED")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
