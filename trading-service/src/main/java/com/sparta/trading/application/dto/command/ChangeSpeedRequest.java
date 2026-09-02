package com.sparta.trading.application.dto.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeSpeedRequest(

        @NotNull
        @Min(1)
        Integer speedFactor
) {
}
