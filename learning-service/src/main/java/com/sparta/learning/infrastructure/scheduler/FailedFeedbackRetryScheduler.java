package com.sparta.learning.infrastructure.scheduler;

import com.sparta.learning.application.service.LearningCommandService;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.model.FeedbackStatus;
import com.sparta.learning.domain.model.FeedbackType;
import com.sparta.learning.infrastructure.config.FeedbackRetryProperties;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

/** AI executor 포화로 만들어지지 못한 피드백을 다시 생성한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FailedFeedbackRetryScheduler {

    // ON_DEMAND_FEEDBACK 제외
    private static final List<FeedbackType> RETRY_TARGET_TYPES =
            List.of(FeedbackType.ENTRY_FEEDBACK, FeedbackType.POSITION_REVIEW);

    // 끊긴 점유를 회수했을 때 남기는 사유 (용량 거부와 구분해 원인을 추적할 수 있게 함)
    private static final String STALE_PROCESSING_REASON = "생성 도중 점유가 끊겨 회수했습니다.";

    // 다시 시도할 가치가 있는 실패 사유.
    private static final List<String> RETRYABLE_FAILURE_REASONS =
            List.of(LearningCommandService.CAPACITY_FAILURE_REASON, STALE_PROCESSING_REASON);

    private final FeedbackRepository feedbackRepository;
    private final LearningCommandService learningCommandService;
    private final FeedbackRetryProperties properties;

    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${learning.feedback.retry.interval:30s}")
    public void retryFailedFeedbacks() {
        if (!properties.isEnabled()) {
            return;
        }

        OffsetDateTime staleBefore = OffsetDateTime.now().minus(properties.getStaleProcessingAfter());
        List<Feedback> targets = feedbackRepository.findRetryTargets(
                RETRY_TARGET_TYPES,
                RETRYABLE_FAILURE_REASONS,
                properties.getMaxAttempts(),
                staleBefore,
                PageRequest.of(0, properties.getBatchSize())
        );

        if (targets.isEmpty()) {
            return;
        }

        log.info("피드백 재처리를 시작합니다. targetCount={}", targets.size());

        int submitted = 0;
        for (Feedback target : targets) {
            if (retry(target)) {
                submitted++;
            }
        }

        log.info("피드백 재처리를 마쳤습니다. 시도={}, 제출 성공={}", targets.size(), submitted);
    }

    // 한 건을 다시 시도한다 (점유는 여기서 안함)
    private boolean retry(Feedback target) {
        try {
            // 방치된 PROCESSING 은 먼저 풀어줘야 한다.
            // prepareGenerationContext 가 PROCESSING 을 "누가 처리 중" 으로 보고 건너뛰기 때문에
            // 그대로 넘기면 아무 일도 일어나지 않는다. FAILED 로 돌려두면 다음 주기에 정상 경로를 탄다.
            if (target.getStatus() == FeedbackStatus.PROCESSING) {
                return releaseStale(target);
            }

            switch (target.getFeedbackType()) {
                case ENTRY_FEEDBACK -> learningCommandService.createEntryFeedback(
                        target.getPositionId(), target.getUserId());
                case POSITION_REVIEW -> learningCommandService.createPositionReviewFeedback(
                        target.getPositionId(), target.getUserId());
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            log.warn("피드백 재처리에 실패했습니다. 다음 주기에 다시 시도합니다. feedbackKey={}, attemptCount={}",
                    target.getFeedbackKey(), target.getAttemptCount(), exception);
            return false;
        }
    }

    // 끊긴 점유를 FAILED 로 되돌린다. 실제 생성은 다음 주기에 일어난다.
    private boolean releaseStale(Feedback target) {
        Integer released = transactionTemplate.execute(status ->
                feedbackRepository.releaseStaleProcessing(
                        target.getId(), STALE_PROCESSING_REASON, OffsetDateTime.now()));

        if (released == null || released == 0) {
            // 그사이 작업이 끝났다는 뜻이다. 건드리지 않는 것이 맞다.
            return false;
        }

        log.info("끊긴 점유를 회수했습니다. 다음 주기에 다시 생성합니다. feedbackKey={}, attemptCount={}",
                target.getFeedbackKey(), target.getAttemptCount());
        return true;
    }
}
