package com.sparta.learning.application.service;

import com.sparta.learning.application.dto.response.AiFeedbackResponse;
import com.sparta.learning.application.port.AiClientPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

//피드백 생성 로직
@Service
@RequiredArgsConstructor
public class LearningCommandService {

    private final AiClientPort aiClientPort;
    // private final FeedbackRepository feedbackRepository;
    // private final ExecutionSnapshotRepository snapshotRepository;
    // private final AiRequestLogRepository aiRequestLogRepository; // ai_request_logs 테이블 저장용

    @Transactional
    public void createOnDemandFeedback(String positionId, String userId) {

        // 1. Snapshot 조회 (Trading DB 대신 내부 테이블 조회)
        // ExecutionSnapshot latestSnapshot = snapshotRepository.findLatestByPositionId(positionId);

        // 2. Feedbacks 테이블에 PENDING 상태로 생성
        // Feedback feedback = new Feedback(positionId, userId, "ON_DEMAND_FEEDBACK", "PENDING");
        // feedbackRepository.save(feedback);

        // 3. AI에 전달할 Context 구성 (스냅샷 + 진단결과를 JSON 화)
        String contextJson = "{ \"average_price\": 100, \"quantity\": 50 }"; // TODO: 실제 데이터 바인딩

        try {
            // 4. AI 호출 및 결과 반환
            AiFeedbackResponse aiResponse = aiClientPort.requestOnDemandFeedback(positionId, contextJson);

            // 5. 성공 시 피드백 업데이트 (COMPLETED) 및 ai_request_logs SUCCESS 저장
            // feedback.updateContent(aiResponse);
            // feedback.complete();
            // aiRequestLogRepository.save(new AiRequestLog(feedback.getId(), "SUCCESS", ...));

        } catch (Exception e) {
            // 6. 실패 시 Fallback 템플릿 처리 및 ai_request_logs FALLBACK 저장
            // feedback.updateContent(FallbackTemplate.get());
            // feedback.fail();
            // aiRequestLogRepository.save(new AiRequestLog(feedback.getId(), "FALLBACK", e.getMessage(), ...));
        }
    }
}