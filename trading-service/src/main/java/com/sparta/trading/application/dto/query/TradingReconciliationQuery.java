package com.sparta.trading.application.dto.query;

import java.util.UUID;

public record TradingReconciliationQuery(
        UUID accountId,
        String checks,
        Boolean includeDetails
) {
}
