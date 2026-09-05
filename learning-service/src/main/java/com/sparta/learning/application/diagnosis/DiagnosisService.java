package com.sparta.learning.application.diagnosis;

import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.rule.DiagnosisRule;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import com.sparta.learning.infrastructure.persistence.repository.DiagnosisResultRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 체결 스냅샷에 진단 규칙을 실행하고 결과를 저장한다
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final List<DiagnosisRule> rules;
    private final DiagnosisResultRepository diagnosisResultRepository;
    private final LearningMetrics learningMetrics;

    // 이 체결에 해당하는 규칙을 실행해 진단 결과를 저장한다.
    // 스냅샷 저장과 같은 트랜젝션에 실행된다 -> 진단 저장이 실패하면 스냅샷도 같이 롤백됨
    @Transactional
    public List<DiagnosisResult> diagnose(ExecutionSnapshot snapshot){
        Timer.Sample sample = learningMetrics.startTimer();
        DiagnosisPhase phase = null;

        try {
            phase = DiagnosisPhase.from(snapshot);
            DiagnosisPhase currentPhase = phase;

            // 1차: 거래 시점에 해당하는 규칙만 고름
            // 2차: 같은 시점이라도 적용 대상인지 확인함 (supports)
            List<DiagnosisResult> results = rules.stream()
                    .filter(rule -> rule.getRuleCode().getDiagnosisPhase() == currentPhase)
                    .filter(rule -> rule.supports(snapshot))
                    .map(rule -> rule.diagnose(snapshot))
                    .toList();

            if(results.isEmpty()){
                learningMetrics.recordDiagnosisSuccess(phase, List.of(), sample);
                return List.of();
            }

            List<DiagnosisResult> newResults = excludeAlreadySaved(results);
            if(newResults.isEmpty()){
                log.info("이미 진단된 체결입니다. executionId = {}, phase = {}", snapshot.getExecutionId(), phase);
                learningMetrics.recordDiagnosisSuccess(phase, List.of(), sample);
                return List.of();
            }

            List<DiagnosisResult> savedResults = diagnosisResultRepository.saveAll(newResults);
            learningMetrics.recordDiagnosisSuccess(phase, savedResults, sample);
            return savedResults;
        } catch (RuntimeException exception) {
            learningMetrics.recordDiagnosisFailure(phase, sample);
            throw exception;
        }
    }


    // 이미 저장된 진단 제외하기
    // 같은 이벤트가 재처리되면 같은 diagnosis_key가 생성되는데, 유니크 제약 위반으로 예외가 나면 트랜젝션 전체가 롤백되므로 저장 전에 걸러냄
    private List<DiagnosisResult> excludeAlreadySaved(List<DiagnosisResult> results){
        // 이번에 만든 진단 키
        Set<String> keys = results.stream()
                .map(DiagnosisResult::getDiagnosisKey)
                .collect(Collectors.toSet());

        // 그중 DB에 이미 저장되어 있는 키
        Set<String> savedKeys = diagnosisResultRepository.findByDiagnosisKeyIn(keys).stream()
                .map(DiagnosisResult::getDiagnosisKey)
                .collect(Collectors.toSet());

        if(savedKeys.isEmpty()){
            return results;
        }

        return results.stream()
                .filter(result -> !savedKeys.contains(result.getDiagnosisKey()))
                .toList();
    }

}
