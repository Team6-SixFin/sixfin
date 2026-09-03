package com.sparta.trading.application.dto.query;

import java.time.Instant;
import java.util.UUID;

public record TradingAdminSearchOrderQuery(UUID userId,
                                           String symbol,
                                           String side,
                                           String status,
                                           Instant from,
                                           Instant to,
                                           String sort,
                                           Integer page,
                                           Integer size) {

    public TradingAdminSearchOrderQuery {
        if (sort == null || sort.isBlank()) sort = "createdAt";
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }

}



