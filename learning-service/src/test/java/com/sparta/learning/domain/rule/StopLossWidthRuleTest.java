package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;
import com.sparta.learning.fixture.ExecutionSnapshotFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 매수가 대비 손절폭이 적절한지 진단하는 규칙을 검증
public class StopLossWidthRuleTest {

    private final StopLossWidthRule rule = new StopLossWidthRule();

    // 매수가 183.1700 기준으로 계산한 경계값
    private static final BigDecimal WIDTH_2_PERCENT = new BigDecimal("179.5066");
    private static final BigDecimal WIDTH_15_PERCENT = new BigDecimal("155.6945");
    private static final BigDecimal WIDTH_3_PERCENT = new BigDecimal("155.6945");

    // 손절가가 없으면 폭을 계산할 수 없다
    @Test
    void 손절가가_없으면_판정하지_않는다(){
        var snapshot = ExecutionSnapshotFixture.firstBuyWithoutStopLoss();

        assertThat(rule.supports(snapshot)).isFalse();
    }

    @Test
    void 손절가가_있어야_판정한다(){
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();
        assertThat(rule.supports(snapshot)).isTrue();
    }

    @Test
    void 손절_폭이_기준_범위_안이면_PASS(){
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();
        DiagnosisResult result = rule.diagnose(snapshot);
        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
    }

    @Test
    void 손절_폭이_2퍼센트_미만이면_WARNNING(){
        var snapshot = ExecutionSnapshotFixture.firstBuy(new BigDecimal("181.0000"));
        DiagnosisResult result = rule.diagnose(snapshot);
        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.WARNING);
    }

    @Test
    void 손절_폭이_정확히_2퍼센트면_PASS(){
        var snapshot = ExecutionSnapshotFixture.firstBuy(WIDTH_2_PERCENT);
        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("2"));
    }

    // 기준이 "15% 초과"이므로 정확히 15%는 통과다
    @Test
    void 손절_폭이_정확히_15퍼센트면_PASS(){
        var snapshot = ExecutionSnapshotFixture.firstBuy(WIDTH_15_PERCENT);
        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getResult()).isEqualTo(DiagnosisStatus.PASS);
        assertThat(result.getMetricValue()).isEqualByComparingTo(new BigDecimal("15"));
    }

    @Test
    void 위반한_방향에_따라_다른_기준값을_남긴다() {
        var tooNarrow = rule.diagnose(ExecutionSnapshotFixture.firstBuy(new BigDecimal("181.0000")));
        var tooWide = rule.diagnose(ExecutionSnapshotFixture.firstBuy(new BigDecimal("150.0000")));

        assertThat(tooNarrow.getThresholdValue()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(tooWide.getThresholdValue()).isEqualByComparingTo(new BigDecimal("15"));
    }

    // 나중에 기준값을 바꿔도 당시 판정 근거를 재구성할 수 있어야 한다
    @Test
    void 판정에_사용한_값이_metrics에_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getMetrics().get("executedPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getExecutedPrice());
        assertThat(result.getMetrics().get("plannedStopLossPrice").decimalValue())
                .isEqualByComparingTo(snapshot.getPlannedStopLossPrice());
        assertThat(result.getMetrics().get("stopLossWidthRate").decimalValue())
                .isEqualByComparingTo(result.getMetricValue());
        assertThat(result.getMetrics().get("minWidthRate").decimalValue())
                .isEqualByComparingTo(new BigDecimal("2"));
        assertThat(result.getMetrics().get("maxWidthRate").decimalValue())
                .isEqualByComparingTo(new BigDecimal("15"));
    }

    // evidence.message는 조회 API가 그대로 응답에 내보내므로 세 경우가 달라야 한다
    @Test
    void 판정_결과에_따라_다른_근거_문구가_담긴다() {
        String appropriate = messageOf(ExecutionSnapshotFixture.firstBuyWithStopLoss());
        String tooNarrow = messageOf(ExecutionSnapshotFixture.firstBuy(new BigDecimal("181.0000")));
        String tooWide = messageOf(ExecutionSnapshotFixture.firstBuy(new BigDecimal("150.0000")));

        assertThat(appropriate).isNotBlank();
        assertThat(tooNarrow).isNotBlank();
        assertThat(tooWide).isNotBlank();
        assertThat(appropriate).isNotEqualTo(tooNarrow);
        assertThat(appropriate).isNotEqualTo(tooWide);
        assertThat(tooNarrow).isNotEqualTo(tooWide);
    }

    @Test
    void 진단_결과에_근거_체결과_규칙_정보가_담긴다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        DiagnosisResult result = rule.diagnose(snapshot);

        assertThat(result.getRuleCode()).isEqualTo(RuleCode.STOP_LOSS_WIDTH.name());
        assertThat(result.getDiagnosisPhase()).isEqualTo(DiagnosisPhase.ENTRY);
        assertThat(result.getUserId()).isEqualTo(snapshot.getUserId());
        assertThat(result.getPositionId()).isEqualTo(snapshot.getPositionId());
        assertThat(result.getExecutionSnapshot()).isSameAs(snapshot);
    }

    // 같은 체결에 같은 규칙을 실행하면 항상 같은 키가 나와야 중복 저장을 막을 수 있다
    @Test
    void 같은_체결은_항상_같은_진단_키를_만든다() {
        var snapshot = ExecutionSnapshotFixture.firstBuyWithStopLoss();

        String first = rule.diagnose(snapshot).getDiagnosisKey();
        String second = rule.diagnose(snapshot).getDiagnosisKey();

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("ENTRY:" + snapshot.getExecutionId() + ":STOP_LOSS_WIDTH:v1");
    }

    private String messageOf(com.sparta.learning.domain.entity.ExecutionSnapshot snapshot) {
        return rule.diagnose(snapshot).getEvidence().get("message").asText();
    }

}
