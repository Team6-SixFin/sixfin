package com.sparta.learning.application.content;

import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.infrastructure.monitoring.LearningMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 완료된 피드백에 학습 자료를 연결한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningResourceLinker {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final FeedbackLearningResourceService feedbackLearningResourceService;
    private final LearningMetrics learningMetrics;

    /** 결과를 기다리지 않는다. 호출한 쪽은 제출만 하고 바로 돌아간다.
     * 학습 자료 추천이 실패해도 이미 완료된 AI 피드백의 성공 상태와 응답에는 영향을 주지 않도록 예외를 여기서 삼킨다.
     */
    @Async("learningResourceTaskExecutor")
    public void linkAsync(Feedback feedback) {
        Timer.Sample sample = learningMetrics.startTimer();

        try {
            int linkedCount = feedbackLearningResourceService.recommendAndLink(
                    feedback.getFeedbackKey(),
                    feedback.getUserId(),
                    feedback.getPositionId(),
                    feedback.getFeedbackType()
            );
            log.info(
                    "피드백 학습 자료 연결 완료. feedbackKey={}, linkedCount={}",
                    feedback.getFeedbackKey(),
                    linkedCount
            );
            learningMetrics.recordLearningResourceRecommendation(feedback.getFeedbackType(), SUCCESS, sample);
        } catch (RuntimeException exception) {
            learningMetrics.recordLearningResourceRecommendation(feedback.getFeedbackType(), FAILED, sample);
            log.warn(
                    "피드백 학습 자료 추천 실패, AI 피드백은 유지합니다. feedbackKey={}",
                    feedback.getFeedbackKey(),
                    exception
            );
        }
    }
}
