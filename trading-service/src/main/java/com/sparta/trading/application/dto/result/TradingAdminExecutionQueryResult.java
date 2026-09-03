package com.sparta.trading.application.dto.result;

import com.sparta.trading.presentation.dto.response.TradingAdminExecutionResponseDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.Map;

public record TradingAdminExecutionQueryResult(
        Map<String, BigDecimal> summary,
        Page<TradingAdminExecutionResponseDto> page
) {
}
