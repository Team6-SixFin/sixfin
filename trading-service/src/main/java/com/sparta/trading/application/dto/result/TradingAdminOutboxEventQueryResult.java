package com.sparta.trading.application.dto.result;

import com.sparta.trading.presentation.dto.response.TradingAdminOutboxEventResponseDto;
import org.springframework.data.domain.Page;

import java.util.Map;

public record TradingAdminOutboxEventQueryResult(
        Map<String, Object> summary,
        Page<TradingAdminOutboxEventResponseDto> page
) {
}
