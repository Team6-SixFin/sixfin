package com.sparta.learning.infrastructure.messaging.kafka.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketContextPayload(
        @NotNull @Positive BigDecimal recent20DayHigh,
        @NotNull @Positive BigDecimal recent20DayLow,
        @NotNull BigDecimal recent5DayReturnRate,
        @NotNull OffsetDateTime quoteTimestamp
) {
}
