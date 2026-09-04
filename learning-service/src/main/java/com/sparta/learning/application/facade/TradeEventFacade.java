package com.sparta.learning.application.facade;

import com.sparta.learning.application.diagnosis.DiagnosisService;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/* 체결 이벤트 처리 순서를 조율한다.
* 이 클래스에는 트랜잭션을 안붙이고 각 서비스가 자기 트랜잭션을 열고 닫는다.
* */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventFacade {

    private final TradeEventIngestionService ingestionService;
    private final DiagnosisService diagnosisService;

    public IngestionResult handle(TradingEventEnvelope event){
        IngestionResult result = ingestionService.ingest(event);

        // 중복 이벤트는 재진단을 안하고 포지션 종료는 아직 진단 대상이 아님
        if(result.hasDiagnosisTarget()){
            runDiagnosis(result);
        }

        return result;
    }

    /*트랜잭션은 분리되어 있으나 예외는 호출 스택을 타고 올라오므로 여기서 차단함*/
    private void runDiagnosis(IngestionResult result) {
        try{
            diagnosisService.diagnose(result.executionSnapshot());
        }
        catch (Exception e){
            log.error("진단 실행에 실패했습니다. executionId={}", result.executionSnapshot().getExecutionId(), e);
        }
    }
}
