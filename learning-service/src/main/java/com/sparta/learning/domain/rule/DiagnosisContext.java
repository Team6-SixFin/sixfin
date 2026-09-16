package com.sparta.learning.domain.rule;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisStatus;
import com.sparta.learning.domain.model.RuleCode;

import java.util.List;

// 규칙이 판정에 사용하는 재료를 담는다
// 규칙은 도메인 계층이라 DB를 조회할 수 없으므로 DiagnosisService가 미리 조회해 전달한다
// 조회 시점이 한 곳으로 고정되어 같은 이벤트를 재처리해도 같은 결과가 나온다
// (이전 진단은 건별이 아니라 규칙 코드와 판정 결과별 집계로 담는다)
public record DiagnosisContext(
        ExecutionSnapshot executionSnapshot,
        ClosedPositionSnapshot closedPositionSnapshot,
        List<PreviousDiagnosisCount> previousCounts
) {

    // ENTRY, TRADE 단계는 체결 하나를 판정한다
    public static DiagnosisContext ofExecution(
            ExecutionSnapshot executionSnapshot,
            List<PreviousDiagnosisCount> previousCounts
    ) {
        return new DiagnosisContext(executionSnapshot, null, previousCounts);
    }

    // CLOSE 단계는 체결 하나가 아니라 포지션 전체를 판정한다
    public static DiagnosisContext ofClosedPosition(
            ClosedPositionSnapshot closedPositionSnapshot,
            List<PreviousDiagnosisCount> previousCounts
    ) {
        return new DiagnosisContext(null, closedPositionSnapshot, previousCounts);
    }

    // 이전 진단에서 특정 규칙이 특정 결과를 낸 횟수를 센다
    public long countPreviousResults(RuleCode ruleCode, DiagnosisStatus status) {
        return previousCounts.stream()
                .filter(previous -> previous.ruleCode().equals(ruleCode.name()))
                .filter(previous -> previous.result() == status)
                .mapToLong(PreviousDiagnosisCount::count)
                .sum();
    }

    // 이전 진단에 해당 결과가 하나라도 있는지 확인
    public boolean hasPreviousResult(RuleCode ruleCode, DiagnosisStatus status) {
        return countPreviousResults(ruleCode, status) > 0;
    }
}
