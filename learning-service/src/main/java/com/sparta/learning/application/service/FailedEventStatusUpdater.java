package com.sparta.learning.application.service;

import com.sparta.learning.domain.entity.FailedEvent;
import com.sparta.learning.global.exception.CustomException;
import com.sparta.learning.global.exception.LearningErrorCode;
import com.sparta.learning.infrastructure.persistence.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* 실패 이벤트의 조회와 상태 변경만 담당한다 */
@Service
@RequiredArgsConstructor
public class FailedEventStatusUpdater {

    private final FailedEventRepository failedEventRepository;

    @Transactional(readOnly = true)
    public FailedEvent loadPending(Long id) {
        FailedEvent failedEvent = findById(id);

        if (failedEvent.isResolved()) {
            throw new CustomException(LearningErrorCode.FAILED_EVENT_ALREADY_RESOLVED);
        }
        return failedEvent;
    }

    @Transactional
    public FailedEvent markResolved(Long id) {
        FailedEvent failedEvent = findById(id);
        failedEvent.resolve();
        return failedEvent;
    }

    /*
     * 재처리가 다시 실패한 경우 사유를 갱신한다.
     * 재처리 트랜잭션과 분리되어 있어 이 갱신은 롤백되지 않음
     */
    @Transactional
    public void markRetryFailed(Long id, String failureReason) {
        findById(id).recordRetryFailure(failureReason);
    }

    private FailedEvent findById(Long id) {
        return failedEventRepository.findById(id)
                .orElseThrow(() -> new CustomException(LearningErrorCode.FAILED_EVENT_NOT_FOUND));
    }
}
