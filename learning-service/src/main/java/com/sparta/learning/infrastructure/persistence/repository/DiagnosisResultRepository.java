package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.DiagnosisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DiagnosisResultRepository extends JpaRepository<DiagnosisResult, Long> {
    // 이미 저장된 진단의 키를 찾는다
    // 규칙마다 조회하지 않게 이번에 만튼 키를 한 번에 넘겨 조회 횟수를 줄임
    List<DiagnosisResult> findByDiagnosisKeyIn(Collection<String> diagnosisKeys);

    // 같은 포지션의 이전 진단 결과를 저장 순서대로 조회한다
    // 반복 여부와 위반 건수를 판정하는 규칙이 사용하며, DiagnosisContext에 담아 전달한다
    List<DiagnosisResult> findByPositionIdOrderByIdAsc(UUID positionId);

    // Ai 피드백 생성을 위한 진단 결과 조회용
    List<DiagnosisResult> findAllByPositionId(UUID positionId);
}
