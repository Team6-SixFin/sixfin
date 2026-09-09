package com.sparta.learning.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.learning.application.dto.query.FailedEventListQuery;
import com.sparta.learning.application.dto.response.FailedEventListItemResponse;
import com.sparta.learning.application.dto.response.FailedEventRetryResponse;
import com.sparta.learning.application.facade.TradeEventFacade;
import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.global.response.PageResponse;
import com.sparta.learning.infrastructure.messaging.kafka.dto.TradingEventEnvelope;
import com.sparta.learning.infrastructure.persistence.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/* 실패 이벤트 조회와 재처리를 담당한다. (상태 갱신만 별도 트랜잭션으로 처리) */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedEventAdminService {

    private final FailedEventRepository failedEventRepository;
    private final FailedEventStatusUpdater statusUpdater;
    private final TradeEventFacade tradeEventFacade;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<FailedEventListItemResponse> getFailedEvents(FailedEventListQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());
        Page<FailedEvent> failedEventPage = failedEventRepository.findAllByQuery(query, pageable);

        List<FailedEventListItemResponse> content = failedEventPage.getContent().stream()
                .map(FailedEventListItemResponse::from)
                .toList();

        return PageResponse.from(failedEventPage, content);
    }

    /* 저장해 둔 원본으로 수집부터 다시 실행 */
    public FailedEventRetryResponse retry(Long id) {
        TradingEventEnvelope event = toEnvelope(statusUpdater.loadPending(id));

        try {
            tradeEventFacade.handle(event);
        } catch (RuntimeException exception) {
            // 재처리 트랜잭션과 분리되어 있어 실패 사유 갱신이 롤백되지 않음
            statusUpdater.markRetryFailed(id, describe(exception));
            log.error("실패 이벤트 재처리에 실패했습니다. id={}, eventId={}", id, event.eventId(), exception);
            throw new CustomException(LearningErrorCode.FAILED_EVENT_RETRY_FAILED);
        }

        FailedEvent resolved = statusUpdater.markResolved(id);
        log.info("실패 이벤트를 재처리했습니다. id={}, eventId={}", id, event.eventId());

        return FailedEventRetryResponse.from(resolved);
    }

    /*
     * 저장 시점과 재처리 시점 사이에 이벤트 계약이 바뀌면 복원에 실패할 수 있다.
     * 이 경우는 파이프라인을 실행조차 못하고 재시도 결과가 동일해 재처리 도중 실패(500)이랑 422로 응답
     */
    private TradingEventEnvelope toEnvelope(FailedEvent failedEvent) {
        try {
            return objectMapper.treeToValue(failedEvent.getPayload(), TradingEventEnvelope.class);
        } catch (Exception exception) {
            log.error("실패 이벤트 payload를 복원할 수 없습니다. id={}", failedEvent.getId(), exception);
            throw new CustomException(LearningErrorCode.FAILED_EVENT_PAYLOAD_BROKEN);
        }
    }

    private String describe(Throwable throwable) {
        return throwable.getClass().getName() + ": " + throwable.getMessage();
    }
}
