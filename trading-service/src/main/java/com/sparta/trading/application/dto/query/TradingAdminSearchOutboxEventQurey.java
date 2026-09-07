package com.sparta.trading.application.dto.query;

import java.time.Instant;
import java.util.UUID;

public record TradingAdminSearchOutboxEventQurey(
        String status,
        String eventType,
        Integer minRetryCount,
        Instant from,
        Instant to,
        Boolean includePayload,
        String sort,
        Integer page,
        Integer size
) {

    public TradingAdminSearchOutboxEventQurey {
        if (sort == null || sort.isBlank()) sort = "createdAt";
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }
}
