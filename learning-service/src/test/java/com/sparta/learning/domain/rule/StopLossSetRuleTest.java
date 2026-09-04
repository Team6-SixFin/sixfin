package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.DiagnosisContextFixture;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 손절가 설정 여부 진단을 검증합니다.

class StopLossSetRuleTest {

    private final StopLossSetRule rule = new StopLossSetRule();

    // 손절가를 설정하고 매수했으면 계획을 세운 것이므로 PASS다
    @Test
    void 손절가가_있으면_PASS() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    // VIOLATION이 아니라 WARNING
    @Test
    void 손절가가_없으면_WARNING() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithoutStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    // 진단 결과가 어느 체결을 근거로 만들어졌는지 추적할 수 있어야 함
    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.STOP_LOSS_SET.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.ENTRY);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }

    // 같은 체결에 같은 규칙을 실행하면 항상 같은 키가 나와야 중복 저장을 막을 수 있다
    @Test
    void 같은_체결은_항상_같은_진단_키를_만든다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        String first = rule.diagnose(DiagnosisContextFixture.of(snapshot)).getDiagnosisKey();
        String second = rule.diagnose(DiagnosisContextFixture.of(snapshot)).getDiagnosisKey();

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("ENTRY:" + snapshot.getExecutionId() + ":STOP_LOSS_SET:v1");
    }

    // 판정 기준이 되는 값은 나중에 근거를 재구성할 수 있도록 남겨야 한다
    @Test
    void 손절가가_있으면_metrics에_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getExecutedPrice());
        assertThat(result.getMetrics().get("plannedStopLossPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getPlannedStopLossPrice());
    }

    // 손절가가 없으면 담을 값이 없으므로 키 자체를 넣지 않는다
    @Test
    void 손절가가_없으면_metrics에_키를_넣지_않는다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithoutStopLoss();

        DiagnosisResult result = rule.diagnose(DiagnosisContextFixture.of(snapshot));

        assertThat(result.getMetrics().has("plannedStopLossPrice")).isFalse();
        assertThat(result.getMetrics().has("executedPrice")).isTrue();
    }

    // evidence.message는 조회 API가 그대로 응답에 내보내므로 비면안된다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String withStopLoss = rule.diagnose(DiagnosisContextFixture.of(ExecutionSnapshotFixture.firstBuyWithStopLoss()))
                .getEvidence().get("message").asText();
        String withoutStopLoss = rule.diagnose(DiagnosisContextFixture.of(ExecutionSnapshotFixture.firstBuyWithoutStopLoss()))
                .getEvidence().get("message").asText();

        assertThat(withStopLoss).isNotBlank();
        assertThat(withoutStopLoss).isNotBlank();
        assertThat(withStopLoss).isNotEqualTo(withoutStopLoss);
    }
}