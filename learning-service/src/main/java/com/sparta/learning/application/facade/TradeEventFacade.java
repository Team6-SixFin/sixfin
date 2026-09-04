package com.sparta.learning.application.facade;

import com.sparta.learning.application.diagnosis.DiagnosisService;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/* 체결 이벤트 처리 순서를 조율한다.
* 이 클래스에는 트랜잭션을 안붙이고 각 서비스가 자기 트랜잭션을 열고 닫는다.
* */
@Component
@RequiredArgsConstructor
public class TradeEventFacade {

    private final TradeEventIngestionService ingestionService;
    private final DiagnosisService diagnosisService;

    public IngestionResult handle(TradingEventEnvelope event){
        IngestionResult result = ingestionService.ingest(event);

        // 최초 처리와 중복 재처리 모두 스냅샷이 있으면 멱등하게 진단한다.
        if(result.hasDiagnosisTarget()){
            diagnosisService.diagnose(result.executionSnapshot());
        }

        return result;
    }
}
