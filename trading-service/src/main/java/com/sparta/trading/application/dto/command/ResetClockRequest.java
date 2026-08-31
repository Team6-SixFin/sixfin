package com.sparta.trading.application.dto.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResetClockRequest(

        @NotNull
        @PositiveOrZero
        Long seq
) {
}
