package com.sparta.learning.application.facade;

import com.sparta.learning.application.diagnosis.DiagnosisService;
import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.model.IngestionResult;
import com.sparta.learning.application.service.LearningCommandService;
import com.sparta.learning.application.service.TradeEventIngestionService;
import com.sparta.learning.domain.entity.ClosedPositionSnapshot;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.model.TradeType;
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
    private final LearningCommandService learningCommandService;

    public IngestionResult handle(TradingEventEnvelope event){
        IngestionResult result = ingestionService.ingest(event);

        // 최초 처리와 중복 재처리 모두 스냅샷이 있으면 멱등하게 진단한다.
        // 체결과 포지션 종료는 진단 대상 타입이 달라 실행 경로를 나눈다.
        if(result.hasExecutionTarget()){
            ExecutionSnapshot snapshot = result.executionSnapshot();

            diagnosisService.diagnose(snapshot);

            // 최초 매수(신규 포지션)인 경우에만 진입 피드백 생성 (동기 호출)
            if (snapshot.getTradeType() == TradeType.BUY && snapshot.isNewPosition()) {
                AiFeedbackResponse feedbackResponse = learningCommandService.createEntryFeedback(snapshot.getPositionId(), snapshot.getUserId());
                log.info("[최초 매수 진입] AI 피드백 생성 완료! 요약: {}", feedbackResponse.summary());
            }

        } else if(result.hasClosedPositionTarget()){
            ClosedPositionSnapshot snapshot = result.closedPositionSnapshot();

            diagnosisService.diagnoseClose(snapshot);

            // 포지션 종료 리뷰 피드백 생성 (동기 호출)
            AiFeedbackResponse feedbackResponse = learningCommandService.createPositionReviewFeedback(snapshot.getPositionId(), snapshot.getUserId());
            log.info("[포지션 종료 리뷰] AI 피드백 생성 완료! 요약: {}", feedbackResponse.summary());
        }

        return result;
    }
}
