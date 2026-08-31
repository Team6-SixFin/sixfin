package com.sparta.learning.application.service;

import com.sparta.learning.application.dto.query.FeedbackListQuery;
import com.sparta.learning.application.dto.response.FeedbackDetailResponse;
import com.sparta.learning.application.dto.response.FeedbackListItemResponse;
import com.sparta.learning.application.dto.response.PositionFeedbackResponse;
import com.sparta.learning.domain.entity.DiagnosisResult;
import com.sparta.learning.domain.entity.ExecutionSnapshot;
import com.sparta.learning.domain.entity.Feedback;
import com.sparta.learning.domain.entity.FeedbackResource;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.global.response.PageResponse;
import com.sparta.learning.infrastructure.persistence.repository.ExecutionSnapshotRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackDetailQueryRepository;
import com.sparta.learning.infrastructure.persistence.repository.FeedbackQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackQueryService {

    private final FeedbackQueryRepository feedbackQueryRepository;
    private final ExecutionSnapshotRepository executionSnapshotRepository;
    private final FeedbackDetailQueryRepository feedbackDetailQueryRepository;

    public PageResponse<FeedbackListItemResponse> getFeedbacks(FeedbackListQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());

        Page<Feedback> feedbackPage = feedbackQueryRepository.findAllByQuery(query, pageable);
        Map<UUID, StockInfo> stockInfoByPosition = findStockInfo(feedbackPage.getContent());

        List<FeedbackListItemResponse> content = feedbackPage.getContent().stream()
                .map(feedback -> {
                    StockInfo stockInfo = stockInfoByPosition.get(feedback.getPositionId());
                    return FeedbackListItemResponse.from(
                            feedback,
                            stockInfo == null ? null : stockInfo.stockSymbol(),
                            stockInfo == null ? null : stockInfo.stockName()
                    );
                })
                .toList();

        return PageResponse.from(feedbackPage, content);
    }

    public FeedbackDetailResponse getFeedbackDetail(UUID userId, Long feedbackId) {
        // ID와 사용자 ID를 함께 조회해 다른 사용자의 피드백 존재 여부를 노출하지 않는다
        Feedback feedback = feedbackDetailQueryRepository.findFeedback(feedbackId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FEEDBACK_NOT_FOUND));

        ExecutionSnapshot firstExecution = executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(feedback.getPositionId(), userId)
                .orElse(null);
        List<DiagnosisResult> diagnosisResults = feedbackDetailQueryRepository.findDiagnoses(feedbackId);
        List<FeedbackResource> feedbackResources = feedbackDetailQueryRepository.findActiveResources(feedbackId);

        return FeedbackDetailResponse.from(
                feedback,
                firstExecution,
                diagnosisResults,
                feedbackResources
        );
    }

    public PositionFeedbackResponse getPositionFeedbacks(UUID userId, UUID positionId) {
        // 최초 매수 결과가 있어야 포지션이 존재, 포지션 존재 여부와 소유권 검증
        ExecutionSnapshot firstExecution = executionSnapshotRepository
                .findFirstByPositionIdAndUserIdOrderByExecutedAtAscIdAsc(positionId, userId)
                .orElseThrow(() -> new CustomException(LearningErrorCode.POSITION_NOT_FOUND));

        List<Feedback> feedbacks = feedbackQueryRepository.findAllByPosition(userId, positionId);
        return PositionFeedbackResponse.from(firstExecution, feedbacks);
    }

    private Map<UUID, StockInfo> findStockInfo(List<Feedback> feedbacks) {
        Set<UUID> positionIds = feedbacks.stream()
                .map(Feedback::getPositionId)
                .collect(Collectors.toSet());

        if (positionIds.isEmpty()) {
            return Map.of();
        }

        // 페이지에 포함된 포지션을 한 번에 조회 -> N+1 문제 방지
        Map<UUID, StockInfo> stockInfoByPosition = new LinkedHashMap<>();
        // 한 포지션에서 추가 매수,매도가 있어도 같은 종목 -> 가장 최초 체결 스냅샷 정보를 사용
        executionSnapshotRepository.findByPositionIdInOrderByExecutedAtAsc(positionIds)
                .forEach(snapshot -> stockInfoByPosition.putIfAbsent(
                        snapshot.getPositionId(),
                        StockInfo.from(snapshot)
                ));

        return stockInfoByPosition;
    }

    private record StockInfo(String stockSymbol, String stockName) {

        private static StockInfo from(ExecutionSnapshot snapshot) {
            return new StockInfo(snapshot.getStockSymbol(), snapshot.getStockName());
        }
    }
}
