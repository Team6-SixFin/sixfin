package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.ClosedPositionSnapshotFixture;
import com.sparta.learning.fixture.DiagnosisContextFixture;
import com.sparta.learning.fixture.DiagnosisResultFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 포지션 종료 시 손절 원칙을 지켰는지 판정하는 규칙을 검증합니다.
// 기준: 손절가보다 낮게 판 매도가 1건 이상이면 VIOLATION
class StopLossAdherenceRuleTest {

    private final StopLossAdherenceRule rule = new StopLossAdherenceRule();

    // CLOSE 단계에는 포지션 종료만 들어오므로 구분할 대상이 없다
    @Test
    void 포지션_종료는_모두_적용_대상이다() {
        assertThat(rule.supports(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithStopLoss()))).isTrue();
        assertThat(rule.supports(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithoutStopLoss()))).isTrue();
    }

    // 사용자가 선언한 계획을 지키지 않은 것이므로 WARNING이 아니라 VIOLATION이다
    @Test
    void 손절가보다_낮게_판_매도가_있으면_VIOLATION() {
        DiagnosisResult result = rule.diagnose(context(1));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.VIOLATION);
    }

    @Test
    void 손절가를_지킨_매도만_있으면_PASS() {
        DiagnosisResult result = rule.diagnose(context(0));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    // 기준이 "1건 이상"이므로 한 건만 있어도 위반이다
    @Test
    void 위반이_한_건이면_VIOLATION() {
        DiagnosisResult result = rule.diagnose(context(1));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.VIOLATION);
        assertThat(result.getMetricValue()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void 위반_건수를_모두_센다() {
        DiagnosisResult result = rule.diagnose(context(3));

        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(result.getMetrics().get("violationCount").asLong()).isEqualTo(3);
    }

    // 다른 규칙의 결과나 PASS는 위반으로 세면 안 된다
    @Test
    void 다른_규칙과_다른_판정은_세지_않는다() {
        List<DiagnosisResult> previous = List.of(
                DiagnosisResultFixture.of(RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.VIOLATION),
                DiagnosisResultFixture.of(RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.PASS),
                DiagnosisResultFixture.of(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING),
                DiagnosisResultFixture.of(RuleCode.STOP_LOSS_SET, DiagnosisStatus.WARNING)
        );

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithStopLoss(), previous));

        assertThat(result.getMetricValue()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // 계획이 없으면 준수 여부를 판단할 기준이 없다
    @Test
    void 계획_손절가가_없으면_NOT_APPLICABLE() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithoutStopLoss()));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.NOT_APPLICABLE);
    }

    // 측정하지 못했으므로 측정값과 기준값을 비워 둔다
    @Test
    void 판정하지_않으면_측정값과_기준값이_비어_있다() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithoutStopLoss()));

        assertThat(result.getMetricValue()).isNull();
        assertThat(result.getThresholdValue()).isNull();
        assertThat(result.getMetrics().has("violationCount")).isFalse();
    }

    // 조회 API가 evidence.message를 그대로 응답에 내보내므로 비면 안 된다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String violated = messageOf(rule.diagnose(context(1)));
        String passed = messageOf(rule.diagnose(context(0)));
        String notApplicable = messageOf(rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithoutStopLoss())));

        assertThat(violated).isNotBlank();
        assertThat(passed).isNotBlank();
        assertThat(notApplicable).isNotBlank();
        assertThat(violated).isNotEqualTo(passed);
        assertThat(passed).isNotEqualTo(notApplicable);
    }

    // 나중에 기준값을 바꿔도 당시 판정 근거를 재구성할 수 있어야 한다
    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        ClosedPositionSnapshot snapshot = ClosedPositionSnapshotFixture.closedWithStopLoss();

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(snapshot,
                        DiagnosisResultFixture.listOf(RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.VIOLATION, 2)));

        assertThat(result.getMetrics().get("plannedStopLossPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getPlannedStopLossPrice());
        assertThat(result.getMetrics().get("averageExitPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getAverageExitPrice());
        assertThat(result.getMetrics().get("realizedReturnRate").decimalValue())
                .isEqualByComparingTo(snapshot.getRealizedReturnRate());
        assertThat(result.getMetrics().get("violationCount").asLong()).isEqualTo(2);
    }

    // ENTRY, TRADE와 달리 포지션 전체가 대상이므로 종료 스냅샷에 연결된다
    @Test
    void 진단_결과에_종료_스냅샷과_규칙_정보가_담긴다() {
        ClosedPositionSnapshot snapshot = ClosedPositionSnapshotFixture.closedWithStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.ofClosed(snapshot));

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.STOP_LOSS_ADHERENCE.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.CLOSE);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getClosedPositionSnapshot()).isSameAs(snapshot);
        assertThat(result.getExecutionSnapshot()).isNull();
    }

    // 체결이 아니라 포지션 단위이므로 키에 positionId가 들어간다
    @Test
    void 같은_포지션은_항상_같은_진단_키를_만든다() {
        ClosedPositionSnapshot snapshot = ClosedPositionSnapshotFixture.closedWithStopLoss();

        String first = rule.diagnose(DiagnosisContextFixture.ofClosed(snapshot)).getDiagnosisKey();
        String second = rule.diagnose(DiagnosisContextFixture.ofClosed(snapshot)).getDiagnosisKey();

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("CLOSE:" + snapshot.getPositionId() + ":STOP_LOSS_ADHERENCE:v1");
    }

    private DiagnosisContext context(int violationCount) {
        return DiagnosisContextFixture.ofClosed(
                ClosedPositionSnapshotFixture.closedWithStopLoss(),
                DiagnosisResultFixture.listOf(
                        RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.VIOLATION, violationCount)
        );
    }

    private String messageOf(DiagnosisResult result) {
        return result.getEvidence().get("message").asText();
    }
}
