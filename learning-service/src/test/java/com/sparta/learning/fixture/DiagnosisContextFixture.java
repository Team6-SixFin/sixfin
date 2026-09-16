package com.sparta.learning.fixture;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.rule.DiagnosisContext;
import com.sparta.learning.domain.rule.PreviousDiagnosisCount;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 규칙 테스트용 진단 컨텍스트를 만듭니다
// 이전 진단이 필요 없는 규칙은 of(snapshot)만 사용
public final class DiagnosisContextFixture {

    private DiagnosisContextFixture() {
    }

    // 이전 진단이 없는 상태의 체결 컨텍스트
    public static DiagnosisContext of(ExecutionSnapshot snapshot) {
        return DiagnosisContext.ofExecution(snapshot, List.of());
    }

    // 이전 진단을 함께 담은 체결 컨텍스트. 반복 여부를 판정하는 규칙 검증에 사용함
    public static DiagnosisContext of(ExecutionSnapshot snapshot, List<DiagnosisResult> previousDiagnoses) {
        return DiagnosisContext.ofExecution(snapshot, countBy(previousDiagnoses));
    }

    // 이전 진단이 없는 상태의 포지션 종료 컨텍스트
    public static DiagnosisContext ofClosed(ClosedPositionSnapshot snapshot) {
        return DiagnosisContext.ofClosedPosition(snapshot, List.of());
    }

    // 이전 진단을 함께 담은 포지션 종료 컨텍스트. CLOSE 규칙의 집계 검증에 사용함
    public static DiagnosisContext ofClosed(ClosedPositionSnapshot snapshot, List<DiagnosisResult> previousDiagnoses) {
        return DiagnosisContext.ofClosedPosition(snapshot, countBy(previousDiagnoses));
    }

    // group by rule_code, result 와 같은 결과를 만든다
    public static List<PreviousDiagnosisCount> countBy(List<DiagnosisResult> previousDiagnoses) {
        return previousDiagnoses.stream()
                .collect(Collectors.groupingBy(
                        result -> Map.entry(result.getRuleCode(), result.getResult()),
                        Collectors.counting()))
                .entrySet().stream()
                .map(counted -> new PreviousDiagnosisCount(
                        counted.getKey().getKey(),
                        counted.getKey().getValue(),
                        counted.getValue()))
                .toList();
    }
}
