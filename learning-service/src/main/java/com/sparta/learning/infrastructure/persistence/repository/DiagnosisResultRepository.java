package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.rule.PreviousDiagnosisCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiagnosisResultRepository extends JpaRepository<DiagnosisResult, Long> {
    // 이미 저장된 진단의 키를 찾는다
    // 규칙마다 조회하지 않게 이번에 만튼 키를 한 번에 넘겨 조회 횟수를 줄임
    List<DiagnosisResult> findByDiagnosisKeyIn(Collection<String> diagnosisKeys);

     /** 같은 포지션에 이미 저장된 진단을 (규칙 코드, 판정 결과) 조합마다 몇 건인지 count한다 */
    @Query("""
            select new com.sparta.learning.domain.rule.PreviousDiagnosisCount(
                       d.ruleCode, d.result, count(d))
              from DiagnosisResult d
             where d.positionId = :positionId
             group by d.ruleCode, d.result
            """)
    List<PreviousDiagnosisCount> countPreviousByPositionId(@Param("positionId") UUID positionId);

    // Ai 피드백 생성을 위한 진단 결과 조회용
    List<DiagnosisResult> findAllByPositionId(UUID positionId);

    // 학습 자료 추천 시 사용자 소유권을 함께 확인하고 최신 진단부터 조회한다
    List<DiagnosisResult> findAllByPositionIdAndUserIdOrderByIdDesc(UUID positionId, UUID userId);
}
