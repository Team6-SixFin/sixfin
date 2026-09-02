package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.Orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TradigAdminOrderResponseDto(UUID orderId,
                                          UUID requestId,
                                          UUID userId,
                                          UUID accountId,
                                          UUID positionId,
                                          String symbol,
                                          String side,
                                          String orderType,
                                          Integer quantity,
                                          String status,
                                          String rejectReason,
                                          BigDecimal plannedStopLossPrice,
                                          Instant marketTime,
                                          Long candleSeq,
                                          LocalDateTime createdAt) {



}
