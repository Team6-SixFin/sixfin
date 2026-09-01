package com.sparta.trading.application.dto.query;

import org.springframework.data.domain.Pageable;

import java.util.UUID;

public record TradingSearchAccountsQuery(UUID userId, String sort, Integer page, Integer size) {

    public TradingSearchAccountsQuery {
        if (sort == null || sort.isBlank()) sort = "createdAt";
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }

}
