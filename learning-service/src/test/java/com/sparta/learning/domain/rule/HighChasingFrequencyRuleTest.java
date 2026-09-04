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

// 포지션 종료 시 고점 추격 매수가 반복됐는지 판정하는 규칙을 검증합니다.
// 기준: 고점 추격 매수가 3회 이상이면 WARNING
class HighChasingFrequencyRuleTest {

    private final HighChasingFrequencyRule rule = new HighChasingFrequencyRule();

    // CLOSE 단계에는 포지션 종료만 들어오므로 구분할 대상이 없다
    @Test
    void 포지션_종료는_모두_적용_대상이다() {
        assertThat(rule.supports(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithStopLoss()))).isTrue();
    }

    @Test
    void 고점_추격이_기준_이상이면_WARNING() {
        DiagnosisResult result = rule.diagnose(context(5));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    // 기준이 "3회 이상"이므로 정확히 3회는 WARNING이다
    @Test
    void 고점_추격이_정확히_3회면_WARNING() {
        DiagnosisResult result = rule.diagnose(context(3));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void 고점_추격이_2회면_PASS() {
        DiagnosisResult result = rule.diagnose(context(2));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("2"));
    }

    // 고점 추격이 없는 것은 정상이므로 0회도 판정 대상이다
    @Test
    void 고점_추격이_없으면_PASS() {
        DiagnosisResult result = rule.diagnose(context(0));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
        assertThat(result.getMetricValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // 다른 규칙의 결과나 PASS는 세면 안 된다
    @Test
    void 다른_규칙과_다른_판정은_세지_않는다() {
        List<DiagnosisResult> previous = List.of(
                DiagnosisResultFixture.of(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING),
                DiagnosisResultFixture.of(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.PASS),
                DiagnosisResultFixture.of(RuleCode.SHORT_TERM_SURGE_BUY, DiagnosisStatus.WARNING),
                DiagnosisResultFixture.of(RuleCode.SELL_BELOW_STOP_LOSS, DiagnosisStatus.VIOLATION)
        );

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithStopLoss(), previous));

        assertThat(result.getMetricValue()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // 손절가 유무와 무관하게 판정할 수 있으므로 NOT_APPLICABLE이 발생하지 않는다
    @Test
    void 계획_손절가가_없어도_판정한다() {
        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(ClosedPositionSnapshotFixture.closedWithoutStopLoss()));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    // 나중에 기준값을 바꿔도 당시 판정 근거를 재구성할 수 있어야 한다
    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        ClosedPositionSnapshot snapshot = ClosedPositionSnapshotFixture.closedWithStopLoss();

        DiagnosisResult result = rule.diagnose(
                DiagnosisContextFixture.ofClosed(snapshot,
                        DiagnosisResultFixture.listOf(RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING, 4)));

        assertThat(result.getMetrics().get("highChasingCount").asLong()).isEqualTo(4);
        assertThat(result.getMetrics().get("thresholdCount").asLong()).isEqualTo(3);
        assertThat(result.getMetrics().get("averageEntryPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getAverageEntryPrice());
        assertThat(result.getMetrics().get("realizedReturnRate").decimalValue())
                .isEqualByComparingTo(snapshot.getRealizedReturnRate());
    }

    // 0회와 1~2회는 같은 PASS지만 사용자에게 전할 내용이 다르다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String warned = messageOf(rule.diagnose(context(3)));
        String passed = messageOf(rule.diagnose(context(2)));
        String none = messageOf(rule.diagnose(context(0)));

        assertThat(warned).isNotBlank();
        assertThat(passed).isNotBlank();
        assertThat(none).isNotBlank();
        assertThat(warned).isNotEqualTo(passed);
        assertThat(passed).isNotEqualTo(none);
    }

    // ENTRY, TRADE와 달리 포지션 전체가 대상이므로 종료 스냅샷에 연결된다
    @Test
    void 진단_결과에_종료_스냅샷과_규칙_정보가_담긴다() {
        ClosedPositionSnapshot snapshot = ClosedPositionSnapshotFixture.closedWithStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.ofClosed(snapshot));

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.HIGH_CHASING_FREQUENCY.name());
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
        assertThat(first).isEqualTo("CLOSE:" + snapshot.getPositionId() + ":HIGH_CHASING_FREQUENCY:v1");
    }

    private DiagnosisContext context(int chasingCount) {
        return DiagnosisContextFixture.ofClosed(
                ClosedPositionSnapshotFixture.closedWithStopLoss(),
                DiagnosisResultFixture.listOf(
                        RuleCode.HIGH_CHASING_BUY, DiagnosisStatus.WARNING, chasingCount)
        );
    }

    private String messageOf(DiagnosisResult result) {
        return result.getEvidence().get("message").asText();
    }
}
