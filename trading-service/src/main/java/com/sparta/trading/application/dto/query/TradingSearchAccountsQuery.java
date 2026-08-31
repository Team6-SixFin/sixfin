package com.sparta.trading.application.dto.query;

import org.springframework.data.domain.Pageable;

import java.util.UUID;

public record TradingSearchAccountsQuery(UUID userId, String sort, Integer page, Integer size) {
}
