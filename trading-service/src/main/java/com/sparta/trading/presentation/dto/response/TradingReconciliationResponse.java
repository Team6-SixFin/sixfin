package com.sparta.trading.presentation.dto.response;

import com.sparta.trading.domain.entity.ReconciliationCheckCode;
import com.sparta.trading.domain.entity.ReconciliationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TradingReconciliationResponse(
        Instant checkedAt,
        String scope,
        int totalAccounts,
        ReconciliationStatus overallStatus,
        long elapsedMs,
        List<CheckResult> checks
) {

    public record CheckResult(
            ReconciliationCheckCode checkCode,
            String name,
            ReconciliationStatus status,
            int mismatchedCount,
            List<Map<String, Object>> details
    ) {
    }
}
