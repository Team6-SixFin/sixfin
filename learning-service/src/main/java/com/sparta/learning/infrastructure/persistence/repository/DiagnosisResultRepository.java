package com.sparta.learning.infrastructure.persistence.repository;

import com.sparta.learning.domain.entity.DiagnosisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiagnosisResultRepository extends JpaRepository<DiagnosisResult, Long> {
    // 이미 저장된 진단의 키를 찾는다
    // 규칙마다 조회하지 않게 이번에 만튼 키를 한 번에 넘겨 조회 횟수를 줄임
    List<DiagnosisResult> findByDiagnosisKeyIn(Collection<String> diagnosisKeys);
}
