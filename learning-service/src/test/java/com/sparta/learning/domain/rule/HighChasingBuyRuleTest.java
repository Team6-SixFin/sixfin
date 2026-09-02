package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 최근 20일 최고가 부근에서 매수했는지 진단하는 규칙을 검증합니다.
// 기준: 20일 최고가 대비 99% 이상이면 WARNING
class HighChasingBuyRuleTest {

    private final HighChasingBuyRule rule = new HighChasingBuyRule();

    // 픽스처의 20일 최고가 187.4000을 기준으로 계산한 매수가
    private static final BigDecimal PRICE_AT_99_PERCENT = new BigDecimal("185.5260");
    private static final BigDecimal PRICE_JUST_BELOW_99 = new BigDecimal("185.5000");
    private static final BigDecimal PRICE_AT_HIGH = new BigDecimal("187.4000");

    // 20일 최고가가 없으면 비율을 계산할 수 없다
    @Test
    void 최고가가_없으면_판정하지_않는다() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent20dHigh(null);

        assertThat(rule.supports(snapshot)).isFalse();
    }

    // 0으로 나누면 예외가 발생하므로 함께 제외한다
    @Test
    void 최고가가_0이면_판정하지_않는다() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent20dHigh(BigDecimal.ZERO);

        assertThat(rule.supports(snapshot)).isFalse();
    }

    @Test
    void 최고가가_있으면_판정한다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        assertThat(rule.supports(snapshot)).isTrue();
    }

    // 픽스처 기본 매수가 183.1700 / 최고가 187.4000 -> 약 97.74%
    @Test
    void 최고가와_거리가_있으면_PASS() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 최고가와_같은_가격에_매수하면_WARNING() {
        var snapshot = ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH);

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    @Test
    void 최고가의_정확히_99퍼센트면_WARNING() {
        var snapshot = ExecutionSnapshotFixture.buyAt(PRICE_AT_99_PERCENT);

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("99"));
    }

    @Test
    void 최고가의_99퍼센트_바로_아래면_PASS() {
        var snapshot = ExecutionSnapshotFixture.buyAt(PRICE_JUST_BELOW_99);

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("98.99"));
    }

    // 나중에 기준값을 바꿔도 당시 판정 근거를 재구성할 수 있어야 한다
    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getExecutedPrice());
        assertThat(result.getMetrics().get("recent20dHigh").decimalValue())
                .isEqualByComparingTo(snapshot.getRecent20dHigh());
        assertThat(result.getMetrics().get("highPriceRatio").decimalValue())
                .isEqualByComparingTo(result.getMetricValue());
        assertThat(result.getMetrics().get("thresholdRatio").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99"));
    }

    // evidence.message는 조회 API가 그대로 응답에 내보내므로 비면 안 된다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String passed = messageOf(ExecutionSnapshotFixture.firstBuyWithStopLoss());
        String warned = messageOf(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH));

        assertThat(passed).isNotBlank();
        assertThat(warned).isNotBlank();
        assertThat(passed).isNotEqualTo(warned);
    }

    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.HIGH_CHASING_BUY.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.ENTRY);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }



    private String messageOf(ExecutionSnapshot snapshot) {
        return rule.diagnose(snapshot).getEvidence().get("message").asText();
    }
}
