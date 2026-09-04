package com.sparta.trading.application.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketContextPayload(
        BigDecimal recent20DayHigh,
        BigDecimal recent20DayLow,
        BigDecimal recent5DayReturnRate,
        OffsetDateTime quoteTimestamp
) {
}
