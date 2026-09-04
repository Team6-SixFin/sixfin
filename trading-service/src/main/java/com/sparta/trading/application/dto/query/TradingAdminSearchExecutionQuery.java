package com.sparta.trading.application.dto.query;

import java.time.Instant;
import java.util.UUID;

public record TradingAdminSearchExecutionQuery(
        UUID userId,
        UUID positionId,
        String symbol,
        String side,
        Instant from,
        Instant to,
        String sort,
        Integer page,
        Integer size
) {

    public TradingAdminSearchExecutionQuery {
        if (sort == null || sort.isBlank()) sort = "createdAt";
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }

}
