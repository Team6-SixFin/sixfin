package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.DiagnosisContextFixture;
import com.sparta.learning.fixture.DiagnosisResultFixture;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 추가 매수가 고점 부근에서 이뤄졌는지 판정하는 규칙을 검증합니다.
// 기준: 이번 매수가 20일 최고가의 99% 이상이면 WARNING
// 이전 경고 유무는 판정 조건이 아니라 근거 문구와 metrics에만 반영됩니다
class RepeatedHighChasingBuyRuleTest {

    private final RepeatedHighChasingBuyRule rule = new RepeatedHighChasingBuyRule();

    // 픽스처의 20일 최고가 187.4000을 기준으로 계산한 매수가
    private static final BigDecimal PRICE_AT_HIGH = new BigDecimal("187.4000");
    private static final BigDecimal PRICE_AT_99_PERCENT = new BigDecimal("185.5260");
    private static final BigDecimal PRICE_JUST_BELOW_99 = new BigDecimal("185.5000");

    // 이전에 고점 추격 경고가 있었던 상태
    private static final List<DiagnosisResult> CHASED_BEFORE = List.of(
            DiagnosisResultFixture.of(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING));

    @Test
    void 매도_체결은_판정하지_않는다() {
        assertThat(rule.supports(DiagnosisContextFixture.of(ExecutionSnapshotFixture.sell()))).isFalse();
    }

    @Test
    void 추가_매수는_적용_대상이다() {
        assertThat(rule.supports(DiagnosisContextFixture.of(ExecutionSnapshotFixture.additionalBuy()))).isTrue();
    }

    @Test
    void 고점_추격이_반복되면_WARNING() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    // 이전 경고를 판정 조건에 넣으면 최초 매수가 고점이 아니었을 때
    // 이후 추가 매수가 모두 고점이어도 판정되지 않는 구간이 생긴다
    @Test
    void 이전_경고가_없어도_고점이면_WARNING() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH)));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    // 이전 REPEATED 경고도 반복 근거로 본다
    // 최초 매수가 고점이 아니어도 추가 매수부터 연쇄가 이어져야 한다
    @Test
    void 이전_추가_매수_경고도_반복_근거가_된다() {
        List<DiagnosisResult> previous = List.of(
                DiagnosisResultFixture.of(RuleCode.REPEATED_HIGH_CHASING_BUY, DiagnosisStatus.WARNING));

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH), previous));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
        assertThat(result.getMetrics().get("chasedBefore").asBoolean()).isTrue();
    }

    @Test
    void 이번_매수가_고점과_거리가_있으면_PASS() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.firstBuyWithStopLoss(), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    // HIGH_CHASING_BUY와 같은 99% 기준을 사용한다
    @Test
    void 정확히_99퍼센트면_반복_판정_대상이다() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_99_PERCENT), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("99"));
    }

    @Test
    void 기준_바로_아래면_PASS() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_JUST_BELOW_99), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    // 이전 진단 중 다른 규칙이나 PASS는 반복 근거가 아니다
    // 판정은 이번 매수만 보므로 WARNING이지만 chasedBefore는 false여야 한다
    @Test
    void 다른_규칙과_다른_판정은_반복으로_보지_않는다() {
        List<DiagnosisResult> previous = List.of(
                DiagnosisResultFixture.of(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.PASS),
                DiagnosisResultFixture.of(RuleCode.SHORT_TERM_SURGE_BUY, DiagnosisStatus.WARNING),
                DiagnosisResultFixture.of(RuleCode.STOP_LOSS_SET, DiagnosisStatus.WARNING)
        );

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH), previous));

        assertThat(result.getMetrics().get("chasedBefore").asBoolean()).isFalse();
    }

    // 20일 최고가가 없으면 비율을 계산할 수 없다
    @Test
    void 최고가가_없으면_NOT_APPLICABLE() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyWithRecent20dHigh(null), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.NOT_APPLICABLE);
    }

    // 비율 계산의 분모이므로 0 이하도 제외한다
    @Test
    void 최고가가_0이면_NOT_APPLICABLE() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(
                        ExecutionSnapshotFixture.buyWithRecent20dHigh(BigDecimal.ZERO), CHASED_BEFORE));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.NOT_APPLICABLE);
    }

    // 측정하지 못했으므로 측정값과 기준값을 비워 둔다
    @Test
    void 판정하지_않으면_측정값과_기준값이_비어_있다() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyWithRecent20dHigh(null)));

        assertThat(result.getMetricValue()).isNull();
        assertThat(result.getThresholdValue()).isNull();
        assertThat(result.getMetrics().has("highPriceRatio")).isFalse();
    }

    // 반복 판정의 근거가 된 이전 경고 여부를 남긴다
    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot, CHASED_BEFORE));

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getExecutedPrice());
        assertThat(result.getMetrics().get("recent20dHigh").decimalValue())
                .isEqualByComparingTo(snapshot.getRecent20dHigh());
        assertThat(result.getMetrics().get("highPriceRatio").decimalValue())
                .isEqualByComparingTo(result.getMetricValue());
        assertThat(result.getMetrics().get("thresholdRatio").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99"));
        assertThat(result.getMetrics().get("chasedBefore").asBoolean()).isTrue();
    }

    // 반복 / 이번이 처음 / 고점과 거리 있음은 판정이 같아도 전할 내용이 다르다
    @Test
    void 반복_여부에_따라_다른_근거_문구가_담긴다() {
        String repeated = messageOf(rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH), CHASED_BEFORE)));
        String firstTime = messageOf(rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH))));
        String notChasing = messageOf(rule.diagnose(
                DiagnosisContextFixture.of(ExecutionSnapshotFixture.firstBuyWithStopLoss(), CHASED_BEFORE)));

        assertThat(repeated).isNotBlank();
        assertThat(firstTime).isNotBlank();
        assertThat(notChasing).isNotBlank();
        assertThat(repeated).isNotEqualTo(firstTime);
        assertThat(firstTime).isNotEqualTo(notChasing);
    }

    // ENTRY가 아니라 TRADE 단계 규칙이다
    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH);

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot, CHASED_BEFORE));

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.REPEATED_HIGH_CHASING_BUY.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.TRADE);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }

    // 같은 체결에 같은 규칙을 실행하면 항상 같은 키가 나와야 중복 저장을 막을 수 있다
    @Test
    void 같은_체결은_항상_같은_진단_키를_만든다() {
        ExecutionSnapshot snapshot = ExecutionSnapshotFixture.buyAt(PRICE_AT_HIGH);

        String first = rule.diagnose(DiagnosisContextFixture.of(snapshot, CHASED_BEFORE)).getDiagnosisKey();
        String second = rule.diagnose(DiagnosisContextFixture.of(snapshot, CHASED_BEFORE)).getDiagnosisKey();

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("TRADE:" + snapshot.getExecutionId() + ":REPEATED_HIGH_CHASING_BUY:v1");
    }

    private String messageOf(DiagnosisResult result) {
        return result.getEvidence().get("message").asText();
    }
}
