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

// 단기 급등 이후의 매수를 진단하는 규칙을 검증합니다.
// 기준: 최근 5거래일 수익률이 10% 이상이면 WARNING
class ShortTermSurgeBuyRuleTest {

    private final ShortTermSurgeBuyRule rule = new ShortTermSurgeBuyRule();

    private static final BigDecimal SURGE_RATE = new BigDecimal("15.0000");
    private static final BigDecimal DECLINE_RATE = new BigDecimal("-8.0000");


    @Test
    void 수익률이_없으면_판정하지_않는다() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(null);

        assertThat(rule.supports(snapshot)).isFalse();
    }

    @Test
    void 수익률이_음수여도_판정한다() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(DECLINE_RATE);

        assertThat(rule.supports(snapshot)).isTrue();
    }

    // 픽스처 기본 수익률 5.2000% = 10% 이상 급등이 아님
    @Test
    void 수익률이_기준_미만이면_PASS() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 하락_이후_매수는_PASS() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(DECLINE_RATE);

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 수익률이_기준_이상이면_WARNING() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(SURGE_RATE);

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    @Test
    void 수익률이_정확히_10퍼센트면_WARNING() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(new BigDecimal("10.0000"));

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void 수익률이_10퍼센트_바로_아래면_PASS() {
        var snapshot = ExecutionSnapshotFixture.buyWithRecent5dReturnRate(new BigDecimal("9.9900"));

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getExecutedPrice());
        assertThat(result.getMetrics().get("recent5dReturnRate").decimalValue())
                .isEqualByComparingTo(snapshot.getRecent5dReturnRate());
        assertThat(result.getMetrics().get("thresholdRate").decimalValue())
                .isEqualByComparingTo(new BigDecimal("10"));
    }

    // evidence.message는 조회 API가 그대로 응답에 내보내므로 비면 안 된다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String passed = messageOf(ExecutionSnapshotFixture.firstBuyWithStopLoss());
        String warned = messageOf(ExecutionSnapshotFixture.buyWithRecent5dReturnRate(SURGE_RATE));

        assertThat(passed).isNotBlank();
        assertThat(warned).isNotBlank();
        assertThat(passed).isNotEqualTo(warned);
    }

    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.SHORT_TERM_SURGE_BUY.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.ENTRY);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }


    private String messageOf(ExecutionSnapshot snapshot) {
        return rule.diagnose(snapshot).getEvidence().get("message").asText();
    }
}
