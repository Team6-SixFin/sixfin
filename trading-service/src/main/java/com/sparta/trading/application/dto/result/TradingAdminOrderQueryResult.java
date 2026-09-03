package com.sparta.trading.application.dto.result;

import com.sparta.trading.presentation.dto.response.TradigAdminOrderResponseDto;
import org.springframework.data.domain.Page;

import java.util.Map;

public record TradingAdminOrderQueryResult(
        Map<String, Long> summary,
        Page<TradigAdminOrderResponseDto> page
) {}