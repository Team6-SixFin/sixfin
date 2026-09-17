package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.infrastructure.messaging.kafka.service.OutboxPublishResult;

public record TradingAdminRetryOutBoxResponse(
        Long outboxId,
        OutboxPublishResult result,
        boolean payloadOverwritten,
        String message
) {

    public static TradingAdminRetryOutBoxResponse of(Long outboxId, OutboxPublishResult result, boolean payloadOverwritten) {
        return new TradingAdminRetryOutBoxResponse(outboxId, result, payloadOverwritten, messageOf(result));
    }

    // 일시적 실패는 스케줄러처럼 나중에 자동으로 재시도되지 않는다 — 이 API를 통해서만 다시 시도된다.
    private static String messageOf(OutboxPublishResult result) {
        return switch (result) {
            case PUBLISHED -> "발행 성공";
            case FAILED -> "발행 실패(영구) - 페이로드를 확인하고 다시 시도해주세요";
            case RETRY_LATER, RETRY_WITHOUT_PAUSE -> "발행 실패(일시적) - 자동 재시도되지 않으니 잠시 후 다시 요청해주세요";
        };
    }
}
