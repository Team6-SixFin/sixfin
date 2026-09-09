package com.sparta.trading.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TradingAdminResetAccountRequest(
        @NotBlank
        @Size(max = 255, message = "reason은 255자 이하여야 합니다.")
        String reason,

        @Positive(message = "initialDeposit은 0보다 커야 합니다.")
        BigDecimal initialDeposit
) {
}
