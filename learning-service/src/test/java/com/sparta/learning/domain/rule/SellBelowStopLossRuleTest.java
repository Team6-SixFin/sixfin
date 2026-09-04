package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.DiagnosisContextFixture;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 계획 손절가보다 낮은 가격에 매도했는지 진단하는 규칙을 검증
class SellBelowStopLossRuleTest {

    private final SellBelowStopLossRule rule = new SellBelowStopLossRule();

    private static final BigDecimal STOP_LOSS_PRICE = new BigDecimal("174.0000");
    private static final BigDecimal BELOW_STOP_LOSS = new BigDecimal("170.0000");
    private static final BigDecimal ABOVE_STOP_LOSS = new BigDecimal("180.0000");

    // TRADE 단계에는 추가 매수도 들어오므로 매도인지 확인해야 한다
    @Test
    void 매수_체결은_판정하지_않는다() {
        var snapshot = ExecutionSnapshotFixture.additionalBuy();

        assertThat(rule.supports(DiagnosisContextFixture.of(snapshot))).isFalse();
    }

    // 손절가 유무와 무관하게 매도는 모두 적용 대상이다
    @Test
    void 매도_체결은_모두_적용_대상이다() {
        assertThat(rule.supports(DiagnosisContextFixture.of(ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE)))).isTrue();
        assertThat(rule.supports(DiagnosisContextFixture.of(ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, null)))).isTrue();
    }

    @Test
    void 계획_손절가가_없으면_NOT_APPLICABLE() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, null);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.NOT_APPLICABLE);
    }

    @Test
    void 판정하지_않으면_측정값과_기준값이_비어_있다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, null);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetricValue()).isNull();
        assertThat(result.getThresholdValue()).isNull();
        assertThat(result.getMetrics().has("plannedStopLossPrice")).isFalse();
    }

    @Test
    void 판정하지_않아도_근거_문구가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, null);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getEvidence().get("message").asText()).isNotBlank();
    }

    @Test
    void 손절가보다_낮게_매도하면_VIOLATION() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.VIOLATION);
    }

    @Test
    void 손절가보다_높게_매도하면_PASS() {
        var snapshot = ExecutionSnapshotFixture.sellAt(ABOVE_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 손절가와_같은_가격에_매도하면_PASS() {
        var snapshot = ExecutionSnapshotFixture.sellAt(STOP_LOSS_PRICE, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 판정_기준은_사용자가_선언한_손절가다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetricValue()).isEqualByComparingTo(BELOW_STOP_LOSS);
        assertThat(result.getThresholdValue()).isEqualByComparingTo(STOP_LOSS_PRICE);
    }

    // 174 -> 170은 손절가 대비 약 2.30% 하회
    @Test
    void 위반하면_하회_폭을_metrics에_담는다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(BELOW_STOP_LOSS);
        assertThat(result.getMetrics().get("plannedStopLossPrice").decimalValue())
                .isEqualByComparingTo(STOP_LOSS_PRICE);
        assertThat(result.getMetrics().get("belowStopLossRate").decimalValue())
                .isEqualByComparingTo(new BigDecimal("2.30"));
    }

    // 위반하지 않았으면 하회 폭이라는 값 자체가 성립하지 않으므로 키를 넣지 않는다
    @Test
    void 위반하지_않으면_하회_폭_키를_넣지_않는다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(ABOVE_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetrics().has("belowStopLossRate")).isFalse();
        assertThat(result.getMetrics().has("executedPrice")).isTrue();
    }

    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String violated = messageOf(ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE));
        String passed = messageOf(ExecutionSnapshotFixture.sellAt(ABOVE_STOP_LOSS, STOP_LOSS_PRICE));

        assertThat(violated).isNotBlank();
        assertThat(passed).isNotBlank();
        assertThat(violated).isNotEqualTo(passed);
    }

    // ENTRY가 아니라 TRADE 단계 규칙이다
    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.SELL_BELOW_STOP_LOSS.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.TRADE);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }

    // 같은 체결에 같은 규칙을 실행하면 항상 같은 키가 나와야 중복 저장을 막을 수 있다
    @Test
    void 같은_체결은_항상_같은_진단_키를_만든다() {
        var snapshot = ExecutionSnapshotFixture.sellAt(BELOW_STOP_LOSS, STOP_LOSS_PRICE);

        String first = rule.diagnose(DiagnosisContextFixture.of(snapshot)).getDiagnosisKey();
        String second = rule.diagnose(DiagnosisContextFixture.of(snapshot)).getDiagnosisKey();

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("TRADE:" + snapshot.getExecutionId() + ":SELL_BELOW_STOP_LOSS:v1");
    }

    private String messageOf(ExecutionSnapshot snapshot) {
        return rule.diagnose(DiagnosisContextFixture.of(snapshot)).getEvidence().get("message").asText();
    }
}
