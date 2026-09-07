package com.sparta.learning.application.diagnosis;

import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.DiagnosisPhase;
import com.sparta.learning.domain.rule.DiagnosisContext;
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
import java.util.UUID;
import java.util.function.Supplier;
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
    // 스냅샷 저장과 별도 트랜잭션으로 실행하며, 실패는 호출자에게 전파해 재시도한다.
    @Transactional
    public List<DiagnosisResult> diagnose(ExecutionSnapshot snapshot){
        DiagnosisPhase phase = DiagnosisPhase.from(snapshot);

        // 규칙은 도메인 계층이라 DB를 조회할 수 없으므로 이전 진단을 미리 담아 전달한다
        // 규칙 실행 전에 한 번만 조회해 모든 규칙이 같은 시점의 데이터를 보게 한다
        return runAndSave(
                () -> DiagnosisContext.ofExecution(snapshot, findPreviousResults(snapshot.getPositionId())),
                phase,
                "executionId = " + snapshot.getExecutionId()
        );
    }

    // 포지션 종료 시 해당하는 규칙을 실행해 진단 결과를 저장한다.
    // 체결 하나가 아니라 포지션 전체가 대상이라 진단 키도 positionId로 만들어진다.
    @Transactional
    public List<DiagnosisResult> diagnoseClose(ClosedPositionSnapshot snapshot){
        // CLOSE 규칙은 그동안 쌓인 진단 결과를 집계해 판정한다
        return runAndSave(
                () -> DiagnosisContext.ofClosedPosition(snapshot, findPreviousResults(snapshot.getPositionId())),
                DiagnosisPhase.CLOSE,
                "positionId = " + snapshot.getPositionId()
        );
    }

    private List<DiagnosisResult> findPreviousResults(UUID positionId){
        return diagnosisResultRepository.findByPositionIdOrderByIdAsc(positionId);
    }

    // 규칙 실행부터 저장까지의 흐름은 체결과 포지션 종료가 동일하다
    // 계측도 여기서 하며, 두 경로가 같은 메트릭에 집계된다
    // 이전 진단 조회 실패도 진단 실패로 계측하도록 Context 생성을 계측 구간 안에서 실행한다
    private List<DiagnosisResult> runAndSave(Supplier<DiagnosisContext> contextSupplier, DiagnosisPhase phase, String target){
        Timer.Sample sample = learningMetrics.startTimer();

        try {
            List<DiagnosisResult> results = execute(contextSupplier.get(), phase);

            if(results.isEmpty()){
                learningMetrics.recordDiagnosisSuccess(phase, List.of(), sample);
                return List.of();
            }

            List<DiagnosisResult> newResults = excludeAlreadySaved(results);
            if(newResults.isEmpty()){
                log.info("이미 진단된 대상입니다. {}, phase = {}", target, phase);
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

    // 1차: 거래 시점에 해당하는 규칙만 고름
    // 2차: 같은 시점이라도 적용 대상인지 확인함 (supports)
    private List<DiagnosisResult> execute(DiagnosisContext context, DiagnosisPhase phase){
        return rules.stream()
                .filter(rule -> rule.getRuleCode().getDiagnosisPhase() == phase)
                .filter(rule -> rule.supports(context))
                .map(rule -> rule.diagnose(context))
                .toList();
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
